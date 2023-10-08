package ru.neosvet.vestnewage.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.Data
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.HomeItem
import ru.neosvet.vestnewage.data.Section
import ru.neosvet.vestnewage.data.SiteTab
import ru.neosvet.vestnewage.helper.HomeHelper
import ru.neosvet.vestnewage.loader.AdditionLoader
import ru.neosvet.vestnewage.loader.CalendarLoader
import ru.neosvet.vestnewage.loader.SiteLoader
import ru.neosvet.vestnewage.loader.SummaryLoader
import ru.neosvet.vestnewage.loader.page.PageLoader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.OnlineObserver
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.AdditionStorage
import ru.neosvet.vestnewage.storage.AdsStorage
import ru.neosvet.vestnewage.storage.JournalStorage
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Files
import ru.neosvet.vestnewage.viewmodel.basic.HomeStrings
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.HomeState
import ru.neosvet.vestnewage.viewmodel.state.ListState
import java.io.BufferedReader
import java.io.FileReader

class HomeToiler : NeoToiler() {

    private enum class Task {
        NONE, OPEN_SUMMARY, OPEN_ADDITION, OPEN_CALENDAR, OPEN_JOURNAL,
        OPEN_NEWS, OPEN_OTHER, LOAD_SUMMARY, LOAD_ADDITION,
        LOAD_CALENDAR, LOAD_NEWS
    }

    var linkSummary: String = ""
        private set
    var linkCalendar: String = ""
        private set
    var linkJournal: String = ""
        private set
    var tabNews = SiteTab.NEWS.value
        private set
    private var pageStorage: PageStorage? = null
    private var adsStorage: AdsStorage? = null
    private var task = Task.NONE
    private lateinit var strings: HomeStrings
    private val client = NeoClient(NeoClient.Type.SECTION)
    private var needLoadCalendar = false
    private var needLoadSummary = false
    private var needLoadNews = false
    private var needLoadAddition = false
    private lateinit var helper: HomeHelper
    private val items = mutableListOf<HomeItem.Type>()
    private val hiddenItems = mutableListOf<HomeItem.Type>()
    private val homeMenu = mutableListOf<Section>()
    private val mainMenu = mutableListOf<Section>()
    private val indexSummary: Int
        get() = items.indexOf(HomeItem.Type.SUMMARY)
    private val indexAddition: Int
        get() = items.indexOf(HomeItem.Type.ADDITION)
    private val indexCalendar: Int
        get() = items.indexOf(HomeItem.Type.CALENDAR)
    private val indexNews: Int
        get() = items.indexOf(HomeItem.Type.NEWS)
    private val indexJournal: Int
        get() = items.indexOf(HomeItem.Type.JOURNAL)
    private val indexMenu: Int
        get() = items.indexOf(HomeItem.Type.MENU)
    private val indexInfo: Int
        get() = items.indexOf(HomeItem.Type.INFO)
    private var isEditor = false
    private var editIndex = -1

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, "Home")
        .putString(Const.MODE, task.toString())
        .build()

    override fun init(context: Context) {
        helper = HomeHelper(context)
        strings = HomeStrings(
            nothing = context.getString(R.string.nothing),
            never = context.getString(R.string.never),
            refreshed = context.getString(R.string.refreshed),
            today_empty = context.getString(R.string.today_empty),
            yesterday = context.resources.getStringArray(R.array.post_days)[0],
            journal = context.getString(R.string.journal),
            calendar = context.getString(R.string.calendar),
            summary = context.getString(R.string.summary),
            news = context.getString(R.string.news),
            book = context.getString(R.string.book),
            markers = context.getString(R.string.markers),
            precept_human_future = context.getString(R.string.precept_human_future),
            additionally_from_tg = context.getString(R.string.additionally_from_tg),
            today_msk = context.getString(R.string.today_msk) + " ",
            back = context.getString(R.string.back),
            last_post_from = context.getString(R.string.last_post_from),
            last_readed = context.getString(R.string.last_readed),
            prom_for_soul_unite = context.getString(R.string.prom_for_soul_unite),
            new_dev_ads = context.getString(R.string.new_dev_ads),
            last = context.getString(R.string.last),
            from = context.getString(R.string.from) + " ",
            new_today = context.getString(R.string.new_today),
            information = context.getString(R.string.information)
        )
        loadItems()
    }

    override suspend fun defaultState() {
        openList(isEditor)
    }

    fun restore() {
        clearStates()
        items.clear()
        mainMenu.clear()
        homeMenu.clear()
        hiddenItems.clear()
        loadItems()
        openList(false)
    }

    fun save() {
        clearStates()
        if (mainMenu.isNotEmpty())
            helper.saveMenu(true, mainMenu)
        helper.saveMenu(false, homeMenu)
        helper.saveItems(items)
        openList(false)
    }

    fun edit() {
        openList(true)
    }

    override suspend fun doLoad() {
        when (task) {
            Task.LOAD_SUMMARY -> loadSummary()
            Task.LOAD_ADDITION -> loadAddition()
            Task.LOAD_CALENDAR -> loadCalendar()
            Task.LOAD_NEWS -> loadNews()
            else -> return
        }
        postState(BasicState.Success)
    }

    override fun onDestroy() {
        pageStorage?.close()
        adsStorage?.close()
    }

    private fun openList(isEditor: Boolean) {
        loadIfNeed = task == Task.NONE
        this.isEditor = isEditor
        scope.launch {
            linkSummary = ""
            linkCalendar = ""
            linkJournal = ""
            val list = mutableListOf<HomeItem>()
            list.addAll(getHomeList(items))
            if (isEditor) {
                initHiddenItems()
                list.addAll(getHomeList(hiddenItems))
            }
            postState(HomeState.Primary(isEditor, list, homeMenu))
            if (needLoadSummary || needLoadAddition || needLoadCalendar || needLoadNews)
                refreshNeed()
        }
    }

    private suspend fun getHomeList(items: List<HomeItem.Type>): List<HomeItem> {
        val list = mutableListOf<HomeItem>()
        items.forEach {
            val item = when (it) {
                HomeItem.Type.CALENDAR -> createCalendarItem()
                HomeItem.Type.NEWS -> createNewsItem()
                HomeItem.Type.ADDITION -> createAdditionItem()
                HomeItem.Type.JOURNAL -> createJournalItem()
                HomeItem.Type.SUMMARY -> createSummaryItem()
                HomeItem.Type.INFO -> createInfoItem()
                else -> HomeItem(it, listOf())
            }
            list.add(item)
        }
        return list
    }

    private fun initHiddenItems() {
        if (hiddenItems.isNotEmpty()) return
        hiddenItems.add(HomeItem.Type.DIV)
        if (indexMenu == -1)
            hiddenItems.add(HomeItem.Type.MENU)
        if (indexCalendar == -1)
            hiddenItems.add(HomeItem.Type.CALENDAR)
        if (indexAddition == -1)
            hiddenItems.add(HomeItem.Type.ADDITION)
        if (indexSummary == -1)
            hiddenItems.add(HomeItem.Type.SUMMARY)
        if (indexNews == -1)
            hiddenItems.add(HomeItem.Type.NEWS)
        if (indexJournal == -1)
            hiddenItems.add(HomeItem.Type.JOURNAL)
        if (indexInfo == -1)
            hiddenItems.add(HomeItem.Type.INFO)
    }

    private suspend fun refreshNeed() {
        if (isRun || !OnlineObserver.isOnline.value) return
        isRun = true
        if (needLoadCalendar) {
            task = Task.LOAD_CALENDAR
            loadCalendar()
            needLoadCalendar = false
        }
        if (needLoadAddition) {
            task = Task.LOAD_ADDITION
            loadAddition()
            needLoadAddition = false
        }
        if (needLoadNews) {
            task = Task.LOAD_NEWS
            loadNews()
            needLoadNews = false
        }
        if (needLoadSummary) {
            task = Task.LOAD_SUMMARY
            loadSummary()
            needLoadSummary = false
        }
        postState(BasicState.Success)
    }

    private fun loadItems() {
        homeMenu.addAll(helper.loadMenu(false))
        items.addAll(helper.loadItems())
    }

    private suspend fun loadSummary() {
        val i = indexSummary
        if (i == -1) return
        postState(HomeState.Loading(i))
        SummaryLoader(client).load()
        readSummaryLink()
        loadPage(linkSummary)
        clearPrimaryState()
        postState(ListState.Update(i, createSummaryItem()))
    }

    private suspend fun loadAddition() {
        val i = indexAddition
        if (i == -1) return
        postState(HomeState.Loading(i))
        AdditionLoader(client).load()
        clearPrimaryState()
        postState(ListState.Update(i, createAdditionItem()))
    }

    private suspend fun loadCalendar() {
        val i = indexCalendar
        if (i == -1) return
        postState(HomeState.Loading(i))
        CalendarLoader(client).load()
        readCalendarLink()
        loadPage(linkCalendar)
        clearPrimaryState()
        postState(ListState.Update(i, createCalendarItem()))
    }

    private fun getAds(): AdsStorage {
        if (adsStorage == null)
            adsStorage = AdsStorage()
        return adsStorage!!
    }

    private fun getPage(date: String): PageStorage {
        if (pageStorage == null)
            pageStorage = PageStorage()
        pageStorage?.open(date)
        return pageStorage!!
    }

    private suspend fun loadNews() {
        val i = indexNews
        if (i == -1) return
        postState(HomeState.Loading(i))
        val loader = SiteLoader(client, getAds())
        loader.load(Urls.Ads)
        clearPrimaryState()
        postState(ListState.Update(i, createNewsItem()))
    }

    private fun loadPage(link: String) {
        if (link.isEmpty()) return
        val loader = PageLoader(client)
        loader.download(link, false)
        loader.finish()
    }

    private suspend fun createJournalItem(): HomeItem {
        if (isEditor)
            return HomeItem(HomeItem.Type.JOURNAL, listOf(strings.journal))
        task = Task.OPEN_JOURNAL
        var title = getJournalTitle()
        while (title == null)
            title = getJournalTitle()
        return HomeItem(
            type = HomeItem.Type.JOURNAL,
            lines = listOf(strings.journal, strings.last_readed, title)
        )
    }

    @SuppressLint("Range")
    private suspend fun getJournalTitle(): String? {
        val journal = JournalStorage()
        var r: String? = strings.nothing
        journal.getLastId()?.let { id ->
            val m = id.split('&')
            val cursor = getPage(m[0]).getPageById(m[1])
            if (cursor.moveToFirst()) {
                r = cursor.getString(cursor.getColumnIndex(Const.TITLE))
                linkJournal = cursor.getString(cursor.getColumnIndex(Const.LINK))
            } else {
                r = null
                journal.delete(id)
            }
            cursor.close()
        }
        journal.close()
        return r
    }

    private fun readCalendarLink() {
        val date = DateUnit.initNow()
        date.changeSeconds(DateUnit.OFFSET_MSK - date.offset)
        val today = date.toAlterString().substring(7).removeRange(6, 8)
        val cursor = getPage(date.my).searchLink(today)
        linkCalendar = if (cursor.moveToFirst())
            cursor.getString(1)
        else ""
        cursor.close()
    }

    @SuppressLint("Range")
    private fun createCalendarItem(): HomeItem {
        if (isEditor)
            return HomeItem(HomeItem.Type.CALENDAR, listOf(strings.calendar))
        task = Task.OPEN_CALENDAR
        val date = DateUnit.initNow()
        date.changeSeconds(DateUnit.OFFSET_MSK - date.offset)
        val cursor = getPage(date.my).getListAll()
        var title = strings.today_empty
        if (cursor.moveToFirst()) {
            val time = cursor.getLong(cursor.getColumnIndex(Const.TIME))
            needLoadCalendar = loadIfNeed && DateUnit.isLongAgo(time)
            val iTitle = cursor.getColumnIndex(Const.TITLE)
            val iLink = cursor.getColumnIndex(Const.LINK)
            var link: String
            val today = date.toAlterString().substring(7).removeRange(6, 8)
            while (cursor.moveToNext()) {
                link = cursor.getString(iLink)
                if (link.contains(today)) {
                    linkCalendar = link
                    title = cursor.getString(iTitle)
                }
            }
        }
        cursor.close()
        val d = date.toAlterString().substring(7)
        return HomeItem(
            type = HomeItem.Type.CALENDAR,
            lines = listOf(strings.calendar, strings.today_msk + d, title)
        )
    }

    private fun readSummaryLink() {
        val file = Files.getFile(Const.RSS)
        if (file.exists()) {
            val br = BufferedReader(FileReader(file))
            br.readLine() //title
            linkSummary = br.readLine() ?: ""
            br.close()
        } else linkSummary = ""
    }

    private fun createSummaryItem(): HomeItem {
        if (isEditor)
            return HomeItem(HomeItem.Type.SUMMARY, listOf(strings.summary))
        task = Task.OPEN_SUMMARY
        val file = Files.getFile(Const.RSS)
        needLoadSummary = loadIfNeed && DateUnit.isLongAgo(file.lastModified())
        val title: String
        val time: Long
        val des: String
        if (file.exists()) {
            val br = BufferedReader(FileReader(file))
            title = br.readLine() ?: strings.nothing
            linkSummary = br.readLine() ?: ""
            br.close()
            time = file.lastModified()
            des = strings.refreshed + HomeItem.PLACE_TIME + strings.back
        } else {
            time = 0L
            title = strings.nothing
            des = strings.never
        }

        return HomeItem(
            type = HomeItem.Type.SUMMARY,
            time = time,
            lines = listOf(strings.summary, des, title)
        )
    }

    private fun createNewsItem(): HomeItem {
        if (isEditor)
            return HomeItem(HomeItem.Type.NEWS, listOf(strings.news))
        task = Task.OPEN_NEWS
        val cursor = getAds().site.getAll()
        var timeItem = 0L
        var timeUpdate = 0L
        if (cursor.moveToFirst()) {
            val iTime = cursor.getColumnIndex(Const.TIME)
            timeUpdate = cursor.getLong(iTime)
            if (cursor.moveToNext()) {
                timeItem = cursor.getLong(iTime)
            }
        }
        cursor.close()
        needLoadNews = loadIfNeed && DateUnit.isLongAgo(timeUpdate)

        var title = strings.nothing
        if (timeItem > 0L) {
            val today = DateUnit.initToday().timeInSeconds
            val diff = today - timeItem / DateUnit.SEC_IN_MILLS
            title = when {
                diff < 0 -> strings.new_today
                diff < DateUnit.DAY_IN_SEC -> strings.last + strings.yesterday
                else -> {
                    val date = DateUnit.putMills(timeItem)
                    strings.last + strings.from + date.toDateString()
                }
            }
        }
        tabNews = SiteTab.NEWS.value
        if (title == strings.nothing || title != strings.new_today) {
            val count = getAds().dev.unreadCount
            if (count > 0) {
                tabNews = SiteTab.DEV.value
                title = strings.new_dev_ads
            }
        }
        val des = if (timeUpdate == 0L) strings.never
        else strings.refreshed + HomeItem.PLACE_TIME + strings.back

        return HomeItem(
            type = HomeItem.Type.NEWS,
            time = timeUpdate,
            lines = listOf(strings.news, des, title)
        )
    }

    private fun createAdditionItem(): HomeItem {
        if (isEditor)
            return HomeItem(HomeItem.Type.ADDITION, listOf(strings.additionally_from_tg))
        task = Task.OPEN_ADDITION
        val file = Files.getFileDB(DataBase.ADDITION)
        needLoadAddition = loadIfNeed && DateUnit.isLongAgo(file.lastModified())
        val time: Long
        val des: String
        if (file.exists()) {
            time = file.lastModified()
            des = strings.refreshed + HomeItem.PLACE_TIME + strings.back
        } else {
            time = 0L
            des = strings.never
        }
        val addition = AdditionStorage()
        addition.open()
        var t = addition.getLastDate()
        addition.close()
        t = if (t.isEmpty()) strings.nothing
        else strings.last_post_from.format(t)
        return HomeItem(
            type = HomeItem.Type.ADDITION,
            time = time,
            lines = listOf(strings.additionally_from_tg, des, t)
        )
    }

    private fun createInfoItem(): HomeItem {
        if (isEditor)
            return HomeItem(HomeItem.Type.INFO, listOf(strings.information))
        task = Task.OPEN_OTHER
        return HomeItem(
            type = HomeItem.Type.INFO,
            lines = listOf(
                strings.prom_for_soul_unite, strings.information,
                strings.precept_human_future
            )
        )
    }

    fun refreshSummary() {
        if (isRun) return
        task = Task.LOAD_SUMMARY
        load()
    }

    fun refreshAddition() {
        if (isRun) return
        task = Task.LOAD_ADDITION
        load()
    }

    fun refreshCalendar() {
        if (isRun) return
        task = Task.LOAD_CALENDAR
        load()
    }

    fun refreshNews() {
        if (isRun) return
        task = Task.LOAD_NEWS
        load()
    }

    fun updateJournal() {
        if (indexJournal == -1) return
        scope.launch {
            postState(ListState.Update(indexJournal, createJournalItem()))
        }
    }

    fun moveUp(index: Int) {
        when {
            index == items.size + 1 -> {
                val item = hiddenItems[1]
                hiddenItems.removeAt(1)
                items.add(item)
            }

            index > items.size -> {
                val n = index - items.size
                val item = hiddenItems[n - 1]
                hiddenItems.removeAt(n - 1)
                hiddenItems.add(n, item)
            }

            else -> {
                val n = index - 1
                val item = items[n]
                items.removeAt(n)
                items.add(index, item)
            }
        }
    }

    fun moveDown(index: Int) {
        when {
            index == items.size - 1 -> {
                val item = items[index]
                items.removeAt(index)
                hiddenItems.add(1, item)
            }

            index < items.size -> {
                val item = items[index]
                items.removeAt(index)
                items.add(index + 1, item)
            }

            else -> {
                val n = index - items.size
                val item = hiddenItems[n]
                hiddenItems.removeAt(n)
                hiddenItems.add(n + 1, item)
            }
        }
    }

    fun editMenu(index: Int, isMain: Boolean) {
        helper.isMain = isMain
        editIndex = index
        val list = if (isMain) {
            if (mainMenu.isEmpty())
                mainMenu.addAll(helper.loadMenu(true))
            helper.getMenuList(mainMenu[index])
        } else helper.getMenuList(homeMenu[index])
        setState(HomeState.Menu(isMain, list))
    }

    fun editMenuItem(newTitle: String) {
        val item = helper.getSectionByTitle(newTitle)
        if (helper.isMain) {
            mainMenu[editIndex] = item
            setState(HomeState.ChangeMainItem(editIndex, helper.getItem(item)))
        } else {
            homeMenu[editIndex] = item
            setState(HomeState.ChangeHomeItem(editIndex, item))
            clearStates()
        }
    }
}