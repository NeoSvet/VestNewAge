package ru.neosvet.vestnewage.viewmodel

import android.annotation.SuppressLint
import androidx.work.Data
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.CalendarItem
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.helper.BookHelper
import ru.neosvet.vestnewage.loader.CalendarLoader
import ru.neosvet.vestnewage.loader.MasterLoader
import ru.neosvet.vestnewage.loader.basic.LoadHandlerLite
import ru.neosvet.vestnewage.loader.page.PageLoader
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.percent
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler

class CalendarToiler : NeoToiler(), LoadHandlerLite {
    var date: DateUnit = DateUnit.initToday().apply { day = 1 }
    private val todayM = date.month
    private val todayY = date.year
    private val prevDate = DateUnit.initToday().apply {
        day = 1
        changeMonth(-1)
    }
    private val calendar = arrayListOf<CalendarItem>()
    private var time: Long = 0
    private var masterLoader: MasterLoader? = null

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, "Calendar")
        .putInt(Const.MONTH, date.month)
        .putInt(Const.YEAR, date.year)
        .putBoolean(Const.UNREAD, isCurMonth()) //updateUnread
        .build()

    override suspend fun doLoad() { //loadFromSite
        loadIfNeed = false
        val list = loadMonth()
        if (loadFromStorage()) {
            postState(NeoState.Calendar(date.calendarString, checkPrev(), checkNext(), calendar))
            postState(NeoState.LongValue(time))
            if (isRun) loadPages(list)
        }
    }

    private fun checkNext(): Boolean {
        val max = DateUnit.initToday().apply { day = 1 }.timeInDays
        return date.timeInDays < max
    }

    private fun checkPrev(): Boolean {
        val days = date.timeInDays
        val isLoadedOtkr = BookHelper().isLoadedOtkr()
        val min = if (isLoadedOtkr)
            BookHelper.MIN_DAYS_OLD_BOOK
        else
            BookHelper.MIN_DAYS_NEW_BOOK
        return days > min
    }

    private suspend fun loadPages(pages: List<String>) {
        val loader = PageLoader()
        var cur = 0
        pages.forEach { link ->
            loader.download(link, false)
            if (isRun.not())
                return@forEach
            cur++
            postState(NeoState.Progress(cur.percent(pages.size)))
        }
        loader.finish()
        isRun = false
        postState(NeoState.Success)
    }

    private fun loadMonth(): List<String> {
        val loader = CalendarLoader()
        loader.setDate(date.year, date.month)
        if (date.year < 2016) {
            if (masterLoader == null)
                masterLoader = MasterLoader(this)
            masterLoader?.loadMonth(date.month, date.year)
        } else
            loader.loadListMonth(isCurMonth())
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
        loadIfNeed = true
        scope.launch {
            if (offsetMonth != 0) {
                date.changeMonth(offsetMonth)
                calendar.clear()
            }
            if (calendar.isEmpty())
                createField()
            if (loadFromStorage()) {
                postState(
                    NeoState.Calendar(
                        date.calendarString,
                        checkPrev(),
                        checkNext(),
                        calendar
                    )
                )
                postState(NeoState.LongValue(time))
            } else {
                postState(NeoState.Calendar(date.calendarString, false, false, calendar))
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
        storage.open(date.my)
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
            if (begin && calendar[i].num == d) {
                return i
            }
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