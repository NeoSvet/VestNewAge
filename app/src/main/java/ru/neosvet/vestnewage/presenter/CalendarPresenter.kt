package ru.neosvet.vestnewage.presenter

import android.annotation.SuppressLint
import androidx.work.*
import ru.neosvet.utils.Const
import ru.neosvet.utils.DataBase
import ru.neosvet.utils.Lib
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.helpers.BookHelper
import ru.neosvet.vestnewage.helpers.DateHelper
import ru.neosvet.vestnewage.helpers.ProgressHelper
import ru.neosvet.vestnewage.list.CalendarItem
import ru.neosvet.vestnewage.model.LoaderModel
import ru.neosvet.vestnewage.presenter.view.CalendarView
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.workers.CalendarWorker
import ru.neosvet.vestnewage.workers.LoaderWorker

class CalendarPresenter(private val view: CalendarView) {
    companion object {
        const val TAG = "calendar"
    }

    var date: DateHelper = DateHelper.initToday().apply { day = 1 }
    private val todayM = date.month
    private val todayY = date.year
    private val calendar = arrayListOf<CalendarItem>()

    fun startLoad() {
        if (ProgressHelper.isBusy() || date.year < 2016) return
        view.showLoading()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(false)
            .build()
        val data = Data.Builder()
            .putString(Const.TASK, this.javaClass.simpleName)
            .putInt(Const.MONTH, date.month)
            .putInt(Const.YEAR, date.year)
            .putBoolean(Const.UNREAD, isCurMonth()) //updateUnread
            .build()
        var task = OneTimeWorkRequest.Builder(CalendarWorker::class.java)
            .setInputData(data)
            .setConstraints(constraints)
            .addTag(TAG)
            .build()
        var job = WorkManager.getInstance(App.context)
            .beginUniqueWork(TAG, ExistingWorkPolicy.REPLACE, task)
        if (!LoaderModel.inProgress) {
            task = OneTimeWorkRequest.Builder(LoaderWorker::class.java)
                .setInputData(data)
                .setConstraints(constraints)
                .addTag(TAG)
                .build()
            job = job.then(task)
        }
        job.enqueue()
    }

    fun changeDate(newDate: DateHelper) {
        date = newDate
        createCalendar(0)
    }

    private fun isCurMonth(): Boolean {
        return date.month == todayM && date.year == todayY
    }

    fun isNeedReload(): Boolean {
        val d = DateHelper.initNow()
        val f = Lib.getFileDB(d.my)
        return !f.exists() || System.currentTimeMillis() - f.lastModified() > DateHelper.HOUR_IN_MILLS
    }

    fun createCalendar(offsetMonth: Int) {
        val d = DateHelper.putDays(date.timeInDays)
        if (offsetMonth != 0) {
            d.changeMonth(offsetMonth)
            date.changeMonth(offsetMonth)
        }
        val prev = if (date.year == 2016 && date.month == 1) {
            val book = BookHelper()
            book.isLoadedOtkr()
        } else !(date.year == 2004 && date.month == 8)
        val next = if (date.year == todayY) date.month != todayM else true
        view.updateData(d.calendarString, prev, next)
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
        openCalendar(true)
    }


    @SuppressLint("Range", "NotifyDataSetChanged")
    fun openCalendar(loadIfNeed: Boolean) {
        try {
            for (i in 0 until calendar.size)
                calendar[i].clear()
            val storage = PageStorage(date.my)
            val cursor = storage.getListAll()
            var empty = true
            if (cursor.moveToFirst()) {
                if (loadIfNeed) {
                    val time = cursor.getLong(cursor.getColumnIndex(Const.TIME))
                    checkTime((time / DateHelper.SEC_IN_MILLS).toInt())
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
                        i = if (link.contains("2009")) 1 else
                            if (link.contains("2004")) 31 else 26
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
            view.updateCalendar(calendar)
            if (empty && loadIfNeed)
                startLoad()
        } catch (e: Exception) {
            e.printStackTrace()
            if (loadIfNeed)
                startLoad()
        }
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

    private fun checkTime(sec: Int) {
        if (isCurMonth()) {
            view.checkTime(sec, true)
            return
        }
        if (date.month == todayM - 1 && date.year == todayY ||
            date.month == 11 && date.year == todayY - 1
        ) {
            val d = DateHelper.putSeconds(sec)
            if (d.month != todayM)
                view.checkTime(sec, false)
        }
    }

    private fun getTitleByLink(s: String): String {
        var result = s
        val storage = PageStorage(DataBase.ARTICLES)
        val curTitle = storage.getTitle(result)
        if (curTitle.moveToFirst())
            result = curTitle.getString(0)
        curTitle.close()
        storage.close()
        return result
    }
}