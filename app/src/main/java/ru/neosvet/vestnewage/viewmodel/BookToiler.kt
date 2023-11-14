package ru.neosvet.vestnewage.viewmodel

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Point
import androidx.work.Data
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.data.BookRnd
import ru.neosvet.vestnewage.data.BookTab
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.helper.BookHelper
import ru.neosvet.vestnewage.helper.DateHelper
import ru.neosvet.vestnewage.loader.BookLoader
import ru.neosvet.vestnewage.loader.MasterLoader
import ru.neosvet.vestnewage.loader.basic.LoadHandlerLite
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.DataBase
import ru.neosvet.vestnewage.storage.JournalStorage
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.*
import ru.neosvet.vestnewage.viewmodel.basic.BookStrings
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.BookState
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.util.*

class BookToiler : NeoToiler(), LoadHandlerLite {
    private var selectedTab = BookTab.POEMS
    private lateinit var dPoems: DateUnit
    private lateinit var dEpistles: DateUnit
    private lateinit var strings: BookStrings
    private var helper: BookHelper? = null
    private val isPoemsTab: Boolean
        get() = selectedTab == BookTab.POEMS
    private val isLoadedOtkr
        get() = DateHelper.isLoadedOtkr()
    private val date: DateUnit
        get() = if (isPoemsTab) dPoems else dEpistles
    private val loader: BookLoader by lazy {
        BookLoader(NeoClient(this))
    }
    private val masterLoader: MasterLoader by lazy {
        MasterLoader(this)
    }
    private val today = DateUnit.initToday()
    private val months = mutableListOf<String>()
    private val yearsP = mutableListOf<String>()
    private val yearsE = mutableListOf<String>()
    private var startYear = -1
    private var startTab = -1
    private val minYear: Int
        get() = if (isPoemsTab || !isLoadedOtkr) 2016 else 2004

    private val minMonth: Int
        get() = if (isPoemsTab && dPoems.year == 2016) 2
        else if (!isPoemsTab && dEpistles.year == 2004) 8
        else 1

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, BookHelper.TAG)
        .putBoolean(Const.FROM_OTKR, isLoadedOtkr)
        .putString(Const.TAB, selectedTab.toString())
        .putString("Date", date.toDateString())
        .build()

    override fun init(context: Context) {
        helper = BookHelper(context).also {
            it.loadDates()
            dPoems = DateUnit.putDays(it.poemsDays)
            dEpistles = DateUnit.putDays(it.epistlesDays)
        }
        strings = BookStrings(
            rndEpistle = context.getString(R.string.rnd_epistle),
            rndPoem = context.getString(R.string.rnd_poem),
            rndVerse = context.getString(R.string.rnd_verse),
            alertRnd = context.getString(R.string.alert_rnd),
            tryAgain = context.getString(R.string.try_again),
            from = context.getString(R.string.from),
            predTolk = context.getString(R.string.pred_tolk)
        )
        context.resources.getStringArray(R.array.months).forEach {
            months.add(it)
        }
        for (i in 2016..today.year)
            yearsP.add(i.toString())
        val m = if (isLoadedOtkr) 2004 else 2016
        for (i in m..2016)
            yearsE.add(i.toString())
    }

    override suspend fun defaultState() {
        if (startYear > -1) {
            if (startTab == 0) dPoems.month = 1 else dEpistles.month = 1
            openList(true, month = -1, year = startYear, tab = startTab)
        } else openList(true, tab = startTab)
    }

    override suspend fun doLoad() {
        currentLoader = loader
        when (selectedTab) {
            BookTab.POEMS -> loader.loadPoemsList(dPoems.year)
            BookTab.EPISTLES -> if (dEpistles.year < 2016) {
                currentLoader = masterLoader
                masterLoader.loadMonth(dEpistles.month, dEpistles.year)
            } else loader.loadEpistlesList()

            BookTab.DOCTRINE -> {
                loader.loadBookList(false)
                openOtherBook()
                if (isRun) loader.loadBook(false, this)
                postState(BasicState.Success)
                return
            }

            BookTab.HOLY_RUS -> {
                loader.loadBookList(true)
                openOtherBook()
                if (isRun) loader.loadBook(true, this)
                postState(BasicState.Success)
                return
            }
        }
        postState(BasicState.Success)
        openList(false)
    }

    override fun onDestroy() {
        helper?.saveDates(dPoems.timeInDays, dEpistles.timeInDays)
    }

    @SuppressLint("Range")
    fun openList(loadIfNeed: Boolean = true, month: Int = -1, year: Int = -1, tab: Int = -1) {
        this.loadIfNeed = loadIfNeed
        if (tab != -1)
            selectedTab = convertTab(tab)
        scope.launch {
            if (selectedTab.value > BookTab.EPISTLES.value) {
                openOtherBook()
                return@launch
            }
            if (month != -1 || year != -1) {
                val d = date
                val days = d.timeInDays
                when (month) {
                    -1 -> {}
                    -2 -> d.changeMonth(1)
                    -3 -> d.changeMonth(-1)
                    else -> d.month = month + minMonth
                }
                if (year > 2000) d.year = year
                else if (year > -1) d.year = year + minYear
                if (isPoemsTab) {
                    if (d.timeInDays > today.timeInDays) d.month = today.month
                    else if (d.timeInDays < DateHelper.MIN_DAYS_POEMS) d.month = 2
                } else if (d.timeInDays > DateHelper.MAX_DAYS_BOOK) d.month = 9
                else if (d.timeInDays < DateHelper.MIN_DAYS_OLD_BOOK) d.month = 8
                if (tab == -1 && days == d.timeInDays) {
                    postState(BasicState.Ready)
                    return@launch
                }
            }
            if (!Files.dateBase(date.my).exists()) {
                postPrimary(0L, listOf())
                if (loadIfNeed) reLoad()
                return@launch
            }
            val list = mutableListOf<BasicItem>()
            if (date.timeInDays == DateHelper.MIN_DAYS_NEW_BOOK && isLoadedOtkr.not())
                list.add(BasicItem(strings.predTolk, Urls.PRED_LINK))
            val storage = PageStorage()
            var t: String
            var s: String
            storage.open(date.my)
            var cursor = storage.getListAll()
            if (cursor.moveToFirst()) {
                val time = cursor.getLong(cursor.getColumnIndex(Const.TIME))
                if (date.year > 2015) { //списки скаченные с сайта Откровений не надо открывать с фильтром - там и так всё по порядку
                    cursor.close()
                    cursor = storage.getList(isPoemsTab)
                    cursor.moveToFirst()
                } else cursor.moveToNext()
                val iTitle = cursor.getColumnIndex(Const.TITLE)
                val iLink = cursor.getColumnIndex(Const.LINK)
                if (cursor.count == 0)
                    postPrimary(time, list) //empty
                else {
                    do {
                        s = cursor.getString(iLink)
                        t = cursor.getString(iTitle)
                        if (s.hasDate && !t.contains(s.date))
                            t += " (" + strings.from + " ${s.date})"
                        list.add(BasicItem(t, s))
                    } while (cursor.moveToNext())
                    postPrimary(time, list)
                }
            } else postPrimary(0L, list) //not loaded
            cursor.close()
            storage.close()
            if (list.isEmpty() && loadIfNeed) {
                val file = Files.dateBase(date.my)
                if (!file.exists() || DateUnit.isLongAgo(file.lastModified()))
                    reLoad()
            }
        }
    }

    private suspend fun postPrimary(time: Long, list: List<BasicItem>) {
        val point = Point(date.year - minYear, date.month - minMonth)
        postState(
            BookState.Primary(
                time = time,
                label = date.calendarString,
                selected = point,
                years = getYears(),
                months = getMonths(),
                list = list
            )
        )
    }

    private fun getMonthsList(min: Int = 0, max: Int = 11): List<String> {
        val list = mutableListOf<String>()
        for (i in min..max)
            list.add(months[i])
        return list
    }

    private fun getMonths(): List<String> =
        if (isPoemsTab) when (dPoems.year) {
            2016 -> getMonthsList(min = 1)
            today.year -> getMonthsList(max = today.month - 1)
            else -> months
        } else when (dEpistles.year) {
            2016 -> getMonthsList(max = 8)
            2004 -> getMonthsList(min = 7)
            else -> months
        }

    private fun getYears(): List<String> =
        if (isPoemsTab) yearsP else yearsE

    private suspend fun openOtherBook() {
        val isRus = selectedTab == BookTab.HOLY_RUS
        val storage = PageStorage()
        storage.open(if (isRus) DataBase.HOLY_RUS else DataBase.DOCTRINE)
        val cursor = storage.getListAll()
        cursor.moveToFirst()
        val site = if (isRus) Urls.HolyRusSite else Urls.DoctrineSite
        if (cursor.count == 1) {
            cursor.close()
            storage.close()
            postState(BookState.Book(site, listOf()))
            reLoad()
            return
        }
        val iTitle = cursor.getColumnIndex(Const.TITLE)
        val iLink = cursor.getColumnIndex(Const.LINK)
        val list = mutableListOf<BasicItem>()
        while (cursor.moveToNext()) {
            val link = cursor.getString(iLink)
            if (isRus || link.isDoctrineBook) {
                val title = cursor.getString(iTitle)
                list.add(BasicItem(title, link))
            }
        }
        cursor.close()
        storage.close()
        if (list.isEmpty()) {
            postState(BookState.Book(site, listOf()))
            reLoad()
        } else postState(BookState.Book(site, list))
    }

    @SuppressLint("Range")
    fun getRnd(type: BookRnd) {
        scope.launch {
            //Определяем диапозон дат:
            val d = DateUnit.initToday()
            var m: Int
            var y: Int
            var maxM = d.month
            var maxY = d.year - 2000
            if (type == BookRnd.POEM) {
                m = 2
                y = 16
            } else {
                if (isLoadedOtkr) {
                    m = 8
                    y = 4
                } else {
                    m = 1
                    y = 16
                }
                if (type == BookRnd.EPISTLE) {
                    maxM = 9
                    maxY = 16
                }
            }
            var n = (maxY - y) * 12 + maxM - m
            if (maxY > 16) { //проверка на существование текущего месяца
                val f = Files.dateBase(d.my)
                if (!f.exists()) n--
            }
            //определяем случайный месяц:
            var g = Random()
            n = g.nextInt(n)
            while (n > 0) { //определяем случайную дату
                if (m == 12) {
                    m = 1
                    y++
                } else m++
                n--
            }
            //открываем базу по случайной дате:
            val name = (if (m < 10) "0" else "") + m + "." + (if (y < 10) "0" else "") + y
            val storage = PageStorage()
            storage.open(name)
            val curTitle: Cursor
            var title: String? = null
            //определяем условие отбора в соотвтствии с выбранным пунктом:
            when (type) {
                BookRnd.POEM -> {
                    title = strings.rndPoem
                    curTitle = storage.getList(true)
                }

                BookRnd.EPISTLE -> {
                    title = strings.rndEpistle
                    curTitle = storage.getList(false)
                }

                BookRnd.VERSE ->
                    curTitle = storage.getListAll()
            }
            //определяем случайных текст:
            if (curTitle.count < 2) n = 0 else {
                g = Random()
                n = g.nextInt(curTitle.count - 2) + 2 //0 - отсуствует, 1 - дата изменения списка
            }
            if (!curTitle.moveToPosition(n)) {
                curTitle.close()
                storage.close()
                postState(BasicState.Message(strings.alertRnd))
                return@launch
            }
            //если случайный текст найден
            var s = ""
            if (type == BookRnd.VERSE) { //случайных стих
                val curPar = storage.getParagraphs(curTitle)
                if (curPar.count > 1) { //если текст скачен
                    g = Random()
                    n = curPar.count //номер случайного стиха
                    if (y > 13 || y == 13 && m > 7) n-- //исключаем подпись
                    n = g.nextInt(n)
                    if (curPar.moveToPosition(n)) { //если случайный стих найден
                        s = curPar.getString(0).fromHTML
                    } else {
                        s = ""
                        n = -1
                    }
                } else s = ""
                curPar.close()
                if (s == "") { //случайный стих не найден
                    postState(BasicState.Message(strings.alertRnd))
                    title = strings.rndVerse
                }
            } else  // случайный катрен или послание
                n = -1
            //выводим на экран:
            val link: String? = curTitle.getString(curTitle.getColumnIndex(Const.LINK))
            var msg: String
            msg = if (link == null) strings.tryAgain
            else storage.getPageTitle(
                curTitle.getString(curTitle.getColumnIndex(Const.TITLE)), link
            )
            curTitle.close()
            if (title == null) {
                title = msg
                msg = s
            }
            postState(
                BookState.Rnd(
                    title = title,
                    link = link ?: "",
                    msg = msg,
                    place = s,
                    par = n
                )
            )
            if (link == null) {
                storage.close()
                return@launch
            }
            //добавляем в журнал:
            val row = ContentValues()
            row.put(Const.TIME, System.currentTimeMillis())
            val dbJournal = JournalStorage()
            row.put(
                DataBase.ID,
                PageStorage.getDatePage(link) + Const.AND + storage.getPageId(link) + Const.AND + n
            )
            storage.close()
            dbJournal.insert(row)
            dbJournal.close()
        }
    }

    override fun postPercent(value: Int) {
        if (isRun) setState(BasicState.Progress(value))
    }

    fun setArgument(tab: Int, year: Int) {
        startTab = tab
        startYear = year
    }

    private fun convertTab(tab: Int) = when (tab) {
        BookTab.EPISTLES.value -> BookTab.EPISTLES
        BookTab.DOCTRINE.value -> BookTab.DOCTRINE
        BookTab.HOLY_RUS.value -> BookTab.HOLY_RUS
        else -> BookTab.POEMS
    }

    fun checkNewDate() {
        scope.launch {
            val file = Files.slash(Files.DATE)
            if (file.exists()) {
                val stream = DataInputStream(
                    BufferedInputStream(App.context.openFileInput(file.name))
                )
                val d = DateUnit.putDays(stream.readInt())
                stream.close()
                if (d.month != date.month || d.year != date.year) {
                    openList(
                        month = d.month - minMonth,
                        year = d.year - minYear
                    )
                }
            }
        }
    }
}