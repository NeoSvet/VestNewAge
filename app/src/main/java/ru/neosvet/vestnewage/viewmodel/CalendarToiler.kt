package ru.neosvet.vestnewage.viewmodel

import android.annotation.SuppressLint
import androidx.work.Data
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.CalendarItem
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.helper.DateHelper
import ru.neosvet.vestnewage.loader.CalendarLoader
import ru.neosvet.vestnewage.loader.MasterLoader
import ru.neosvet.vestnewage.loader.basic.LoadHandlerLite
import ru.neosvet.vestnewage.loader.page.PageLoader
import ru.neosvet.vestnewage.service.LoaderService
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.percent
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler

class CalendarToiler : NeoToiler(), LoadHandlerLite {
    var date: DateUnit = DateUnit.initToday().apply { day = 1 }
        private set
    private val todayM = date.month
    private val todayY = date.year
    private val prevDate = DateUnit.initToday().apply {
        day = 1
        changeMonth(-1)
    }
    private val calendar = arrayListOf<CalendarItem>()
    private var time: Long = 0
    private val masterLoader: MasterLoader by lazy {
        MasterLoader(this)
    }
    private val pageLoader: PageLoader by lazy {
        PageLoader()
    }
    var isUpdateUnread = false
        private set

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, "Calendar")
        .putInt(Const.MONTH, date.month)
        .putInt(Const.YEAR, date.year)
        .putBoolean(Const.UNREAD, isUpdateUnread)
        .build()

    override suspend fun doLoad() {
        loadIfNeed = false
        val list = loadMonth()
        if (loadFromStorage()) {
            postState(NeoState.Calendar(date.calendarString, calendar))
            postState(NeoState.LongValue(time))
            if (isRun) loadPages(list)
        }
    }

    fun checkNext(): Boolean {
        val max = DateUnit.initToday().apply { day = 1 }.timeInDays
        return date.timeInDays < max
    }

    fun checkPrev(): Boolean {
        val days = date.timeInDays
        val min = if (DateHelper.isLoadedOtkr())
            DateHelper.MIN_DAYS_OLD_BOOK
        else if (days == DateHelper.MIN_DAYS_NEW_BOOK) {
            // доступна для того, чтобы предложить скачать Послания за 2004-2015
            return !LoaderService.isRun
        } else
            DateHelper.MIN_DAYS_NEW_BOOK
        return days > min
    }

    private suspend fun loadPages(pages: List<String>) {
        currentLoader = pageLoader
        if (pageLoader.isFinish.not()) {
            isRun = false
            while (pageLoader.isFinish.not())
                delay(100) //wait cancel prev loadPages
            isRun = true
        }
        var i = 0
        while (i < pages.size && isRun) {
            pageLoader.download(pages[i], false)
            i++
            postState(NeoState.Progress(i.percent(pages.size)))
        }
        pageLoader.finish()
        if (isRun) {
            isRun = false
            postState(NeoState.Success)
        }
    }

    private fun loadMonth(): List<String> {
        val loader = CalendarLoader()
        loader.setDate(date.year, date.month)
        isUpdateUnread = isCurMonth()
        if (date.year < 2016) {
            currentLoader = masterLoader
            masterLoader.loadMonth(date.month, date.year)
        } else {
            currentLoader = loader
            loader.loadListMonth(isUpdateUnread)
        }
        return loader.getLinkList()
    }

    fun changeDate(newDate: DateUnit) {
        if (isRun) return
        date = newDate
        calendar.clear()
        openCalendar(0)
    }

    private fun isCurMonth(): Boolean {
        return date.month == todayM && date.year == todayY
    }

    fun isNeedReload(): Boolean {
        val d = DateUnit.initNow()
        val f = Lib.getFileDB(d.my)
        return !f.exists() || DateUnit.isLongAgo(f.lastModified())
    }

    fun openCalendar(offsetMonth: Int) {
        isUpdateUnread = false
        loadIfNeed = true
        scope.launch {
            if (offsetMonth != 0) {
                date.changeMonth(offsetMonth)
                calendar.clear()
            }
            if (calendar.isEmpty())
                createField()
            if (loadFromStorage()) {
                postState(NeoState.Calendar(date.calendarString, calendar))
                postState(NeoState.LongValue(time))
            } else {
                postState(NeoState.Calendar(date.calendarString, calendar))
                postState(NeoState.LongValue(0))
                isRun = true
                reLoad()
            }
        }
    }

    private fun createField() {
        val d = DateUnit.putDays(date.timeInDays)
        for (i in -1 downTo -6)  //add label monday-saturday
            calendar.add(CalendarItem(i, R.color.light_gray))
        calendar.add(CalendarItem(0, R.color.light_gray)) //sunday
        val curMonth = d.month
        if (d.dayWeek != DateUnit.MONDAY.toInt()) {
            if (d.dayWeek == DateUnit.SUNDAY.toInt()) d.changeDay(-6) else d.changeDay(1 - d.dayWeek)
            while (d.month != curMonth) {
                calendar.add(CalendarItem(d.day, android.R.color.darker_gray))
                d.changeDay(1)
            }
        }
        val today = DateUnit.initToday()
        var n = 0
        if (today.month == curMonth)
            n = today.day
        while (d.month == curMonth) {
            calendar.add(CalendarItem(d.day, android.R.color.white))
            if (d.day == n)
                calendar.last().setBold()
            d.changeDay(1)
        }
        while (d.dayWeek != DateUnit.MONDAY.toInt()) {
            calendar.add(CalendarItem(d.day, android.R.color.darker_gray))
            d.changeDay(1)
        }
    }

    @SuppressLint("Range", "NotifyDataSetChanged")
    private suspend fun loadFromStorage(): Boolean {
        for (i in 0 until calendar.size)
            calendar[i].clear()
        val storage = PageStorage()
        storage.open(date.my, false)
        val cursor = storage.getListAll()
        var empty = true
        if (cursor.moveToFirst()) {
            time = cursor.getLong(cursor.getColumnIndex(Const.TIME))
            if (loadIfNeed && checkTime((time / DateUnit.SEC_IN_MILLS))) {
                cursor.close()
                storage.close()
                return false
            }
            val iTitle = cursor.getColumnIndex(Const.TITLE)
            val iLink = cursor.getColumnIndex(Const.LINK)
            var i: Int
            var title: String
            var link: String
            while (cursor.moveToNext()) {
                title = cursor.getString(iTitle)
                link = cursor.getString(iLink)
                if (link.contains("@")) {
                    i = link.substring(0, 2).toInt()
                    link = link.substring(9)
                } else if (link.contains("predislovie")) {
                    i = when {
                        link.contains("2009") -> 1
                        link.contains("2004") -> 31
                        else -> 26
                    }
                } else {
                    i = link.lastIndexOf("/") + 1
                    i = link.substring(i, i + 2).toInt()
                }
                i = getIndexByDay(i)
                if (i == -1) continue
                calendar[i].addLink(link)
                if (storage.existsPage(link)) {
                    title = storage.getPageTitle(title, link)
                    if (title.contains("."))
                        title = title.substring(title.indexOf(" ") + 1)
                } else
                    title = getTitleByLink(link)
                calendar[i].addTitle(title)
                empty = false
            }
        }
        cursor.close()
        storage.close()
        if (empty && loadIfNeed)
            return false
        return true
    }

    private fun getIndexByDay(d: Int): Int {
        var begin = false
        for (i in calendar.indices) {
            if (calendar[i].num == 1) {
                if (begin) return -1
                begin = true
            }
            if (begin && calendar[i].num == d)
                return i
        }
        return -1
    }

    private fun checkTime(sec: Long): Boolean {
        if (date.my != prevDate.my)
            return false
        val d = DateUnit.putSeconds(sec.toInt())
        if (d.month == prevDate.month)
            return true
        return false
    }

    private fun getTitleByLink(s: String): String {
        val storage = PageStorage()
        storage.open(DataBase.ARTICLES)
        val result = storage.getTitle(s)
        storage.close()
        return result
    }

    override fun postPercent(value: Int) {
        setState(NeoState.Progress(value))
    }
}