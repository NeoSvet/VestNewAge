package ru.neosvet.vestnewage.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
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
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Files
import ru.neosvet.vestnewage.utils.dateFromLink
import ru.neosvet.vestnewage.utils.hasDate
import ru.neosvet.vestnewage.utils.percent
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.CalendarState

class CalendarToiler : NeoToiler(), LoadHandlerLite {
    private var date = DateUnit.initToday().apply { day = 1 }
    private val todayM = date.month
    private val todayY = date.year
    private val calendar = arrayListOf<CalendarItem>()
    private var time = 0L
    private val client = NeoClient(NeoClient.Type.SECTION)
    private val masterLoader: MasterLoader by lazy {
        MasterLoader(this)
    }
    private val pageLoader: PageLoader by lazy {
        PageLoader(client)
    }
    private val today = DateUnit.initToday()
    private val months = mutableListOf<String>()
    private val years = mutableListOf<String>()
    private var isUpdateUnread = false
    private var minYear = 0
    private val minMonth: Int
        get() = if (date.year == 2004) 8 else 1

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, "Calendar")
        .putInt(Const.MONTH, date.month)
        .putInt(Const.YEAR, date.year)
        .putBoolean(Const.UNREAD, isUpdateUnread)
        .build()

    override fun init(context: Context) {
        context.resources.getStringArray(R.array.months).forEach {
            months.add(it)
        }
        minYear = if (DateHelper.isLoadedOtkr()) 2004 else 2016
        for (i in minYear..today.year)
            years.add(i.toString())
    }

    override suspend fun defaultState() {
        loadIfNeed = true
        isUpdateUnread = true
        createField()
        val load = !loadFromStorage()
        postPrimary()
        if (load || isNeedReload(time)) reLoad()
    }

    private suspend fun postPrimary() {
        val point = Point(date.year - minYear, date.month - minMonth)
        postState(
            CalendarState.Primary(
                time = time,
                label = date.calendarString,
                selected = point,
                years = years,
                months = getMonths(),
                isUpdateUnread = isUpdateUnread,
                list = calendar
            )
        )
    }

    override suspend fun doLoad() {
        loadIfNeed = false
        val list = loadMonth()
        if (loadFromStorage()) {
            postPrimary()
            if (isRun) loadPages(list)
        }
        postState(BasicState.Success)
    }

    private fun getMonthsList(min: Int = 0, max: Int = 11): List<String> {
        val list = mutableListOf<String>()
        for (i in min..max)
            list.add(months[i])
        return list
    }

    private fun getMonths(): List<String> = when (date.year) {
        2004 -> getMonthsList(min = 7)
        today.year -> getMonthsList(max = today.month - 1)
        else -> months
    }

    private suspend fun loadPages(pages: List<String>) {
        currentLoader = pageLoader
        if (pageLoader.isFinish.not()) {
            isRun = false
            while (pageLoader.isFinish.not())
                delay(50) //wait cancel prev loadPages
        }
        isRun = true
        var i = 0
        while (i < pages.size && isRun) {
            pageLoader.download(pages[i], false)
            i++
            postState(BasicState.Progress(i.percent(pages.size)))
        }
        pageLoader.finish()
    }

    private fun loadMonth(): List<String> {
        val loader = CalendarLoader(client)
        loader.setDate(date.year, date.month)
        if (date.year < 2016) {
            currentLoader = masterLoader
            masterLoader.loadMonth(date.month, date.year)
        } else {
            currentLoader = loader
            loader.loadListMonth(isUpdateUnread)
        }
        return loader.getLinkList()
    }

    private fun isCurMonth(): Boolean {
        return date.month == todayM && date.year == todayY
    }

    fun openList(month: Int = -1, year: Int = -1) {
        isUpdateUnread = false
        loadIfNeed = true
        scope.launch {
            if (month != -1 || year != -1) {
                val days = date.timeInDays
                when (month) {
                    -1 -> {}
                    -2 -> date.changeMonth(1)
                    -3 -> date.changeMonth(-1)
                    else -> date.month = month + minMonth
                }
                if (year > -1) date.year = year + minYear
                if (date.timeInDays > today.timeInDays) date.month = today.month
                if (date.timeInDays < DateHelper.MIN_DAYS_OLD_BOOK) date.month = 8
                if (days == date.timeInDays) {
                    postState(BasicState.Ready)
                    return@launch
                }
                calendar.clear()
            }
            if (calendar.isEmpty())
                createField()
            if (!Files.getFileDB(date.my).exists()) {
                time = 0L
                postPrimary()
                if (loadIfNeed) reLoad()
            } else {
                val isEmpty = !loadFromStorage()
                postPrimary()
                if (isEmpty) postState(BasicState.Empty)
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
                calendar.last().isBold = true
            d.changeDay(1)
        }
        while (d.dayWeek != DateUnit.MONDAY.toInt()) {
            calendar.add(CalendarItem(d.day, android.R.color.darker_gray))
            d.changeDay(1)
        }
    }

    @SuppressLint("Range", "NotifyDataSetChanged")
    private fun loadFromStorage(): Boolean {
        for (i in 0 until calendar.size)
            calendar[i].clear()
        if (date.timeInDays == DateHelper.MIN_DAYS_NEW_BOOK && DateHelper.isLoadedOtkr().not())
            calendar[getIndexByDay(1)].addLink(Urls.PRED_LINK)
        val storage = PageStorage()
        storage.open(date.my)
        val cursor = storage.getListAll()
        var empty = true
        time = 0L
        if (cursor.moveToFirst()) {
            time = cursor.getLong(cursor.getColumnIndex(Const.TIME))
            val iTitle = cursor.getColumnIndex(Const.TITLE)
            val iLink = cursor.getColumnIndex(Const.LINK)
            var i: Int
            var title: String
            var link: String
            while (cursor.moveToNext()) {
                link = cursor.getString(iLink)
                title = cursor.getString(iTitle) ?: link
                i = getIndexByDay(link.dateFromLink.day)
                if (i == -1) continue
                calendar[i].addLink(link)
                if (storage.existsPage(link)) {
                    title = storage.getPageTitle(title, link)
                    if (title.contains("."))
                        title = title.substring(title.indexOf(" ") + 1)
                } else if (!link.hasDate)
                    title = getTitleByLink(link)
                calendar[i].addTitle(title)
                empty = false
            }
        }
        cursor.close()
        storage.close()
        if (empty)
            return false
        return true
    }

    private fun getIndexByDay(d: Int): Int {
        var i = d + 6
        while (i < calendar.size) {
            if (calendar[i].num == d) return i
            i++
        }
        return -1
    }

    private fun isNeedReload(time: Long): Boolean {
        if (isCurMonth())
            return DateUnit.isLongAgo(time)
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
        if (isRun) setState(BasicState.Progress(value))
    }
}