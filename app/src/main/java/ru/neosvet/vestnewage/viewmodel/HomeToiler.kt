package ru.neosvet.vestnewage.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.Data
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.HomeItem
import ru.neosvet.vestnewage.data.Section
import ru.neosvet.vestnewage.data.SiteTab
import ru.neosvet.vestnewage.helper.HomeHelper
import ru.neosvet.vestnewage.loader.SiteLoader
import ru.neosvet.vestnewage.loader.UpdateLoader
import ru.neosvet.vestnewage.loader.page.PageLoader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.OnlineObserver
import ru.neosvet.vestnewage.storage.AdditionStorage
import ru.neosvet.vestnewage.storage.DataBase
import ru.neosvet.vestnewage.storage.DevStorage
import ru.neosvet.vestnewage.storage.JournalStorage
import ru.neosvet.vestnewage.storage.NewsStorage
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

    private var titleSummary = ""
    private var linkSummary = ""
    private var linkCalendar = ""
    var tabNews = SiteTab.NEWS.value
        private set
    private var pageStorage: PageStorage? = null
    private var task = Task.NONE
    private lateinit var strings: HomeStrings
    private val client = NeoClient()
    private val loader = UpdateLoader(client)
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
            new = context.getString(R.string.new_section),
            on_tab = context.getString(R.string.on_tab),
            never = context.getString(R.string.never),
            refreshed = context.getString(R.string.refreshed),
            today_empty = context.getString(R.string.today_empty),
            yesterday = context.resources.getStringArray(R.array.post_days)[0],
            journal = context.getString(R.string.journal),
            calendar = context.getString(R.string.calendar),
            summary = context.getString(R.string.summary),
            doctrine = context.getString(R.string.doctrine),
            academy = context.getString(R.string.academy),
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
            information = context.getString(R.string.information),
            help_edit = context.getString(R.string.help_edit)
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
        loader.cancel()
        pageStorage?.close()
    }

    private fun openList(isEditor: Boolean) {
        loadIfNeed = task == Task.NONE
        this.isEditor = isEditor
        scope.launch {
            titleSummary = ""
            linkSummary = ""
            linkCalendar = ""
            val list = mutableListOf<HomeItem>()
            if (isEditor)
                list.add(HomeItem(HomeItem.Type.HELP, listOf(strings.help_edit)))
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

    private fun getHomeList(items: List<HomeItem.Type>): List<HomeItem> {
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
        isRun = false
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
        val list = loader.checkSummary(true)
        val isRss = if (list.isNotEmpty) {
            titleSummary = strings.new + ": " + list.first().first
            linkSummary = list.first().second
            loadPage(linkSummary)
            clearPrimaryState()
            true
        } else false
        val isDoc = loader.checkDoctrine()
        val isAc = loader.checkAcademy()
        if (isDoc || isAc) {
            val sb = StringBuilder(strings.new)
            sb.append(strings.on_tab)
            if (isRss) {
                sb.append(strings.summary)
                sb.append(", ")
            }
            if (isDoc) {
                sb.append(strings.doctrine)
                sb.append(", ")
            }
            if (isAc)
                sb.append(strings.academy)
            else sb.delete(sb.length - 2, sb.length)
            titleSummary = sb.toString()
            linkSummary = ""
        }
        postState(ListState.Update(i, createSummaryItem()))
    }

    private suspend fun loadAddition() {
        val i = indexAddition
        if (i == -1) return
        postState(HomeState.Loading(i))
        loader.checkAddition()
        clearPrimaryState()
        postState(ListState.Update(i, createAdditionItem()))
    }

    private suspend fun loadCalendar() {
        val i = indexCalendar
        if (i == -1) return
        postState(HomeState.Loading(i))
        loader.checkCalendar()?.let {
            linkCalendar = it
            loadPage(linkCalendar)
            clearPrimaryState()
            postState(ListState.Update(i, createCalendarItem()))
            return
        }
        postState(HomeState.Loading(i))
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
        val loader = SiteLoader(client)
        loader.loadAds()
        clearPrimaryState()
        postState(ListState.Update(i, createNewsItem()))
    }

    private fun loadPage(link: String) {
        if (link.isEmpty()) return
        val loader = PageLoader(client)
        loader.download(link, true)
        loader.finish()
    }

    private fun createJournalItem(): HomeItem {
        if (isEditor)
            return HomeItem(HomeItem.Type.JOURNAL, listOf(strings.journal))
        task = Task.OPEN_JOURNAL
        val journal = JournalStorage()
        val p = journal.getLastItem() ?: Pair(strings.nothing, "")
        return HomeItem(
            type = HomeItem.Type.JOURNAL,
            lines = listOf(strings.journal, strings.last_readed, p.first, p.second)
        )
    }

    @SuppressLint("Range")
    private fun createCalendarItem(): HomeItem {
        if (isEditor)
            return HomeItem(HomeItem.Type.CALENDAR, listOf(strings.calendar))
        task = Task.OPEN_CALENDAR
        var title = strings.today_empty
        val date = DateUnit.initMskNow()
        if (linkCalendar.isEmpty()) {
            val cursor = getPage(date.my).getListAll()
            if (cursor.moveToFirst()) {
                val time = cursor.getLong(cursor.getColumnIndex(Const.TIME))
                needLoadCalendar = loadIfNeed && DateUnit.isLongAgo(time)
                val iTitle = cursor.getColumnIndex(Const.TITLE)
                val iLink = cursor.getColumnIndex(Const.LINK)
                var link: String
                val today = date.toShortDateString()
                while (cursor.moveToNext()) {
                    link = cursor.getString(iLink)
                    if (link.contains(today)) {
                        linkCalendar = link
                        title = cursor.getString(iTitle)
                    }
                }
            }
            cursor.close()
        } else {
            title = getPage(date.my).getTitle(linkCalendar, true)
        }
        val d = date.toDateString()
        return HomeItem(
            type = HomeItem.Type.CALENDAR,
            lines = listOf(strings.calendar, strings.today_msk + d, title, linkCalendar)
        )
    }

    private fun createSummaryItem(): HomeItem {
        if (isEditor)
            return HomeItem(HomeItem.Type.SUMMARY, listOf(strings.summary))
        task = Task.OPEN_SUMMARY
        val file = Files.file(Files.RSS)
        needLoadSummary = loadIfNeed && DateUnit.isLongAgo(file.lastModified())
        val time: Long
        val des: String
        if (file.exists()) {
            if (titleSummary.isEmpty()) {
                val br = BufferedReader(FileReader(file))
                titleSummary = br.readLine() ?: strings.nothing
                linkSummary = br.readLine() ?: ""
                br.close()
            }
            time = file.lastModified()
            des = strings.refreshed + HomeItem.PLACE_TIME + strings.back
        } else {
            time = 0L
            titleSummary = strings.nothing
            des = strings.never
        }

        return HomeItem(
            type = HomeItem.Type.SUMMARY,
            time = time,
            lines = listOf(strings.summary, des, titleSummary, linkSummary)
        )
    }

    private fun createNewsItem(): HomeItem {
        if (isEditor)
            return HomeItem(HomeItem.Type.NEWS, listOf(strings.news))
        task = Task.OPEN_NEWS
        val storage = NewsStorage()
        val cursor = storage.getAll()
        var timeItem = 0L
        var timeUpdate = 0L
        if (cursor.moveToFirst()) {
            val iTime = cursor.getColumnIndex(Const.TIME)
            timeUpdate = cursor.getLong(iTime)
            if (cursor.moveToNext())
                timeItem = cursor.getLong(iTime)
        }
        cursor.close()
        storage.close()
        needLoadNews = loadIfNeed && DateUnit.isLongAgo(timeUpdate)

        var title = strings.nothing
        if (timeItem > 0L) {
            val today = DateUnit.initToday()
            val date = DateUnit.putMills(timeItem)
            title = when (date.toShortDateString()) {
                today.toShortDateString() ->
                    strings.new_today

                today.apply { changeDay(-1) }.toShortDateString() ->
                    strings.last + strings.yesterday

                else ->
                    strings.last + strings.from + date.toDateString()
            }
        }
        tabNews = SiteTab.NEWS.value
        if (title == strings.nothing || title != strings.new_today) {
            val dev = DevStorage()
            if (dev.unreadCount > 0) {
                tabNews = SiteTab.DEV.value
                title = strings.new_dev_ads
            }
            dev.close()
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
        val file = Files.dateBase(DataBase.ADDITION)
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
        val i = index - 1
        when {
            i == items.size + 1 -> {
                val item = hiddenItems[1]
                hiddenItems.removeAt(1)
                items.add(item)
            }

            i > items.size -> {
                val n = i - items.size
                val item = hiddenItems[n - 1]
                hiddenItems.removeAt(n - 1)
                hiddenItems.add(n, item)
            }

            else -> {
                val n = i - 1
                val item = items[n]
                items.removeAt(n)
                items.add(i, item)
            }
        }
    }

    fun moveDown(index: Int) {
        val i = index - 1
        when {
            i == items.size - 1 -> {
                val item = items[i]
                items.removeAt(i)
                hiddenItems.add(1, item)
            }

            i < items.size -> {
                val item = items[i]
                items.removeAt(i)
                items.add(i + 1, item)
            }

            else -> {
                val n = i - items.size
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