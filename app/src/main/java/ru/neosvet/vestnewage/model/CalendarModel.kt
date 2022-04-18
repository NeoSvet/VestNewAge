package ru.neosvet.vestnewage.model

import android.annotation.SuppressLint
import androidx.work.Data
import kotlinx.coroutines.launch
import ru.neosvet.utils.Const
import ru.neosvet.utils.DataBase
import ru.neosvet.utils.Lib
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.helpers.BookHelper
import ru.neosvet.vestnewage.helpers.DateHelper
import ru.neosvet.vestnewage.helpers.ProgressHelper
import ru.neosvet.vestnewage.list.CalendarItem
import ru.neosvet.vestnewage.loader.CalendarLoader
import ru.neosvet.vestnewage.loader.PageLoader
import ru.neosvet.vestnewage.model.basic.NeoState
import ru.neosvet.vestnewage.model.basic.NeoViewModel
import ru.neosvet.vestnewage.model.basic.ProgressState
import ru.neosvet.vestnewage.model.basic.SuccessCalendar
import ru.neosvet.vestnewage.storage.PageStorage

class CalendarModel : NeoViewModel() {
    companion object {
        const val TAG = "calendar"
    }

    var date: DateHelper = DateHelper.initToday().apply { day = 1 }
    private val todayM = date.month
    private val todayY = date.year
    private val prevDate = DateHelper.initToday().apply {
        day = 1
        changeMonth(-1)
    }
    private val calendar = arrayListOf<CalendarItem>()
    private val prev: Boolean
        get() {
            return if (date.year == 2016 && date.month == 1) {
                val book = BookHelper()
                book.isLoadedOtkr()
            } else !(date.year == 2004 && date.month == 8)
        }
    private val next: Boolean
        get() = if (date.year == todayY) date.month != todayM else true

    override fun onDestroy() {
    }

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, TAG)
        .putInt(Const.MONTH, date.month)
        .putInt(Const.YEAR, date.year)
        .putBoolean(Const.UNREAD, isCurMonth()) //updateUnread
        .build()

    override suspend fun doLoad() { //loadFromSite
        if (date.year < 2016) return
        val list = loadMonth()
        if (loadFromStorage()) {
            mstate.postValue(SuccessCalendar(date.calendarString, prev, next, calendar))
            loadPages(list)
        }
    }

    private fun loadPages(pages: List<String>) {
        val max = pages.size.toFloat()
        val loader = PageLoader(false)
        var cur = 0f
        pages.forEach { link ->
            loader.download(link, false)
            if (isRun.not())
                return@forEach
            cur++
            mstate.postValue(ProgressState(ProgressHelper.getPercent(cur, max)))
        }
        loader.finish()
    }

    private fun loadMonth(): List<String> {
        val loader = CalendarLoader()
        loader.setDate(date.year, date.month)
        loader.loadListMonth(isCurMonth())
        return loader.getLinkList()
    }

    fun changeDate(newDate: DateHelper) {
        date = newDate
        openCalendar(0)
    }

    private fun isCurMonth(): Boolean {
        return date.month == todayM && date.year == todayY
    }

    fun isNeedReload(): Boolean {
        val d = DateHelper.initNow()
        val f = Lib.getFileDB(d.my)
        return !f.exists() || System.currentTimeMillis() - f.lastModified() > DateHelper.HOUR_IN_MILLS
    }

    fun openCalendar(offsetMonth: Int, loadIfNeed: Boolean = true) {
        this.loadIfNeed = loadIfNeed
        scope.launch {
            if (offsetMonth != 0)
                date.changeMonth(offsetMonth)

            if (calendar.isEmpty() || offsetMonth != 0) {
                createField()
                mstate.postValue(SuccessCalendar(date.calendarString, false, false, calendar))
            }
            if (loadFromStorage())
                mstate.postValue(SuccessCalendar(date.calendarString, prev, next, calendar))
            else {
                mstate.postValue(NeoState.Loading)
                doLoad()
            }
        }
    }

    private fun createField() {
        val d = DateHelper.putDays(date.timeInDays)
        calendar.clear()
        for (i in -1 downTo -6)  //add label monday-saturday
            calendar.add(CalendarItem(i, R.color.light_gray))
        calendar.add(CalendarItem(0, R.color.light_gray)) //sunday
        val curMonth = d.month
        if (d.dayWeek != DateHelper.MONDAY.toInt()) {
            if (d.dayWeek == DateHelper.SUNDAY.toInt()) d.changeDay(-6) else d.changeDay(1 - d.dayWeek)
            while (d.month != curMonth) {
                calendar.add(CalendarItem(d.day, android.R.color.darker_gray))
                d.changeDay(1)
            }
        }
        val today = DateHelper.initToday()
        var n = 0
        if (today.month == curMonth)
            n = today.day
        while (d.month == curMonth) {
            calendar.add(CalendarItem(d.day, android.R.color.white))
            if (d.day == n)
                calendar.last().setBold()
            d.changeDay(1)
        }
        while (d.dayWeek != DateHelper.MONDAY.toInt()) {
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
            if (loadIfNeed) {
                val time = cursor.getLong(cursor.getColumnIndex(Const.TIME))
                if (checkTime((time / DateHelper.SEC_IN_MILLS))) {
                    loadIfNeed = false
                    cursor.close()
                    storage.close()
                    return false
                }
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
        if (empty && loadIfNeed) {
            loadIfNeed = false
            return false
        }
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
        val d = DateHelper.putSeconds(sec.toInt())
        if (d.month == prevDate.month)
            return true
        return false
    }

    private fun getTitleByLink(s: String): String {
        var result = s
        val storage = PageStorage()
        storage.open(DataBase.ARTICLES)
        val curTitle = storage.getTitle(result)
        if (curTitle.moveToFirst())
            result = curTitle.getString(0)
        curTitle.close()
        storage.close()
        return result
    }
}