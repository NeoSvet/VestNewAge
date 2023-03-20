package ru.neosvet.vestnewage.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.Data
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.HomeItem
import ru.neosvet.vestnewage.loader.AdditionLoader
import ru.neosvet.vestnewage.loader.CalendarLoader
import ru.neosvet.vestnewage.loader.SiteLoader
import ru.neosvet.vestnewage.loader.SummaryLoader
import ru.neosvet.vestnewage.loader.page.PageLoader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.NetConst
import ru.neosvet.vestnewage.network.OnlineObserver
import ru.neosvet.vestnewage.storage.AdditionStorage
import ru.neosvet.vestnewage.storage.JournalStorage
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.viewmodel.basic.HomeStrings
import ru.neosvet.vestnewage.viewmodel.basic.ListEvent
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import java.io.BufferedReader
import java.io.FileReader
import java.util.zip.CRC32

class HomeToiler : NeoToiler() {
    companion object {
        private const val INDEX_SUMMARY = 0
        private const val INDEX_ADDITION = 1
        private const val INDEX_CALENDAR = 2
        private const val INDEX_NEWS = 3
    }

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
    private val storage: PageStorage by lazy {
        PageStorage()
    }
    private var task = Task.NONE
    private lateinit var strings: HomeStrings
    private val client = NeoClient(NeoClient.Type.SECTION)
    private var needLoadCalendar = false
    private var needLoadSummary = false
    private var needLoadNews = false
    private var needLoadAddition = false
    private var newsCRC = 0L

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, "Home")
        .putString(Const.MODE, task.toString())
        .build()

    fun init(context: Context) {
        onLoading = false
        if (task == Task.NONE) {
            strings = HomeStrings(
                nothing = context.getString(R.string.nothing),
                never = context.getString(R.string.never),
                refreshed = context.getString(R.string.refreshed),
                today_empty = context.getString(R.string.today_empty),
                journal = context.getString(R.string.journal),
                calendar = context.getString(R.string.calendar),
                summary = context.getString(R.string.summary),
                news = context.getString(R.string.news),
                book = context.getString(R.string.book),
                markers = context.getString(R.string.markers),
                about_prom_time = context.getString(R.string.about_prom_time),
                additionally_from_tg = context.getString(R.string.additionally_from_tg),
                today_msk = context.getString(R.string.today_msk) + " ",
                back = context.getString(R.string.back),
                last_post_from = context.getString(R.string.last_post_from),
                last_readed = context.getString(R.string.last_readed),
                prom_for_soul_unite = context.getString(R.string.prom_for_soul_unite),
                no_changes = context.getString(R.string.no_changes),
                has_changes = context.getString(R.string.has_changes)
            )
        }
    }

    override fun onDestroy() {
        storage.close()
    }

    fun openList() {
        loadIfNeed = task == Task.NONE
        scope.launch {
            linkSummary = ""
            linkCalendar = ""
            linkJournal = ""
            val list = mutableListOf<HomeItem>()
            list.add(createSummaryItem())
            list.add(createAdditionItem())
            list.add(createCalendarItem())
            list.add(createNewsItem())
            list.add(createJournalItem())
            task = Task.OPEN_OTHER
            list.add(
                HomeItem(
                    type = HomeItem.Type.PROM,
                    isRefresh = false,
                    line1 = strings.prom_for_soul_unite,
                    line2 = "",
                    line3 = strings.about_prom_time
                )
            )
            list.add(
                HomeItem(
                    type = HomeItem.Type.MENU,
                    isRefresh = false,
                    line1 = "",
                    line2 = "",
                    line3 = ""
                )
            )
            postState(NeoState.HomeList(list))
            if (needLoadSummary || needLoadAddition || needLoadCalendar || needLoadNews)
                refreshOld()
        }
    }

    private suspend fun refreshOld() {
        if (isRun || !OnlineObserver.isOnline.value) return
        isRun = true
        if (needLoadSummary) {
            task = Task.LOAD_SUMMARY
            loadSummary()
            needLoadSummary = false
            delay(100)
        }
        if (needLoadAddition) {
            task = Task.LOAD_ADDITION
            loadAddition()
            needLoadAddition = false
            delay(100)
        }
        if (needLoadCalendar) {
            task = Task.LOAD_CALENDAR
            loadCalendar()
            needLoadCalendar = false
            delay(100)
        }
        if (needLoadNews) {
            task = Task.LOAD_NEWS
            loadNews()
            needLoadNews = false
            delay(100)
        }
        postState(NeoState.Success)
    }

    override suspend fun doLoad() {
        when (task) {
            Task.LOAD_SUMMARY -> loadSummary()
            Task.LOAD_ADDITION -> loadAddition()
            Task.LOAD_CALENDAR -> loadCalendar()
            Task.LOAD_NEWS -> loadNews()
            else -> return
        }
        postState(NeoState.Success)
    }

    private suspend fun loadSummary() {
        postState(NeoState.ListState(ListEvent.RELOAD, INDEX_SUMMARY))
        SummaryLoader(client).load()
        readSummaryLink()
        loadPage(linkSummary)
        clearPrimaryState()
        postState(NeoState.HomeUpdate(createSummaryItem()))
    }

    private suspend fun loadAddition() {
        postState(NeoState.ListState(ListEvent.RELOAD, INDEX_ADDITION))
        AdditionLoader(client).load()
        clearPrimaryState()
        postState(NeoState.HomeUpdate(createAdditionItem()))
    }

    private suspend fun loadCalendar() {
        postState(NeoState.ListState(ListEvent.RELOAD, INDEX_CALENDAR))
        CalendarLoader(client).load()
        readCalendarLink()
        loadPage(linkCalendar)
        clearPrimaryState()
        postState(NeoState.HomeUpdate(createCalendarItem()))
    }

    private suspend fun loadNews() {
        postState(NeoState.ListState(ListEvent.RELOAD, INDEX_NEWS))
        val loader = SiteLoader(client, Lib.getFile(SiteToiler.NEWS).toString())
        loader.load(NetConst.SITE + SiteToiler.NOVOSTI)
        clearPrimaryState()
        postState(NeoState.HomeUpdate(createNewsItem()))
    }

    private fun loadPage(link: String) {
        if (link.isEmpty()) return
        val loader = PageLoader(client)
        loader.download(link, false)
        loader.finish()
    }

    @SuppressLint("Range")
    private suspend fun createJournalItem(): HomeItem {
        task = Task.OPEN_JOURNAL
        val journal = JournalStorage()
        var title = strings.nothing
        journal.getLastId()?.let { id ->
            val m = id.split('&')
            storage.open(m[0])
            val cursor = storage.getPageById(m[1])
            if (cursor.moveToFirst()) {
                title = cursor.getString(cursor.getColumnIndex(Const.TITLE))
                linkJournal = cursor.getString(cursor.getColumnIndex(Const.LINK))
            }
            cursor.close()
        }
        storage.close()
        return HomeItem(
            type = HomeItem.Type.JOURNAL,
            isRefresh = false,
            line1 = strings.journal,
            line2 = strings.last_readed,
            line3 = title
        )
    }

    private suspend fun readCalendarLink() {
        val date = DateUnit.initNow()
        date.changeSeconds(DateUnit.OFFSET_MSK - date.offset)
        storage.open(date.my)
        val today = date.toAlterString().substring(7).removeRange(6, 8)
        val cursor = storage.searchLink(today)
        linkCalendar = if (cursor.moveToFirst())
            cursor.getString(1)
        else ""
        cursor.close()
    }

    @SuppressLint("Range")
    private suspend fun createCalendarItem(): HomeItem {
        task = Task.OPEN_CALENDAR
        val date = DateUnit.initNow()
        date.changeSeconds(DateUnit.OFFSET_MSK - date.offset)
        storage.open(date.my)
        val cursor = storage.getListAll()
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
        return HomeItem(
            type = HomeItem.Type.CALENDAR,
            isRefresh = true,
            line1 = strings.calendar,
            line2 = strings.today_msk + date.toAlterString().substring(7),
            line3 = title
        )
    }

    private suspend fun readSummaryLink() {
        val file = Lib.getFile(Const.RSS)
        if (file.exists()) {
            val br = BufferedReader(FileReader(file))
            br.readLine() //title
            linkSummary = br.readLine() ?: ""
            br.close()
        } else linkSummary = ""
    }

    private suspend fun createSummaryItem(): HomeItem {
        task = Task.OPEN_SUMMARY
        val file = Lib.getFile(Const.RSS)
        needLoadSummary = loadIfNeed && DateUnit.isLongAgo(file.lastModified())
        val title: String
        val time: String
        if (file.exists()) {
            val br = BufferedReader(FileReader(file))
            title = br.readLine() ?: strings.nothing
            linkSummary = br.readLine() ?: ""
            br.close()
            val diff = DateUnit.getDiffDate(System.currentTimeMillis(), file.lastModified())
            time = strings.refreshed + diff
        } else {
            title = strings.nothing
            time = strings.never
        }

        return HomeItem(
            type = HomeItem.Type.SUMMARY,
            isRefresh = true,
            line1 = strings.summary,
            line2 = time + strings.back,
            line3 = title
        )
    }

    private suspend fun createNewsItem(): HomeItem {
        task = Task.OPEN_NEWS
        val file = Lib.getFile(SiteToiler.NEWS)
        needLoadNews = loadIfNeed && DateUnit.isLongAgo(file.lastModified())
        val crc32 = CRC32()
        crc32.update(file.readBytes())
        if (newsCRC == 0L)
            newsCRC = crc32.value
        //TODO check dev news?
        val title: String
        val time: String
        if (file.exists()) {
            title = if (newsCRC == crc32.value) strings.no_changes
            else strings.has_changes
            val diff = DateUnit.getDiffDate(System.currentTimeMillis(), file.lastModified())
            time = strings.refreshed + diff
        } else {
            title = strings.nothing
            time = strings.never
        }

        return HomeItem(
            type = HomeItem.Type.NEWS,
            isRefresh = true,
            line1 = strings.news,
            line2 = time + strings.back,
            line3 = title
        )
    }

    private suspend fun createAdditionItem(): HomeItem {
        task = Task.OPEN_ADDITION
        val file = Lib.getFileDB(DataBase.ADDITION)
        needLoadAddition = loadIfNeed && DateUnit.isLongAgo(file.lastModified())
        val time = if (file.exists()) {
            val diff = DateUnit.getDiffDate(System.currentTimeMillis(), file.lastModified())
            strings.refreshed + diff
        } else strings.never
        val addition = AdditionStorage()
        addition.open()
        var t = addition.getLastDate()
        addition.close()
        t = if (t.isEmpty()) strings.nothing
        else strings.last_post_from.format(t)
        return HomeItem(
            type = HomeItem.Type.ADDITION,
            isRefresh = true,
            line1 = strings.additionally_from_tg,
            line2 = time + strings.back,
            line3 = t
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
        scope.launch {
            postState(NeoState.HomeUpdate(createJournalItem()))
        }
    }
}