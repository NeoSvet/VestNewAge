package ru.neosvet.vestnewage.viewmodel

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import androidx.work.Data
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.data.BookRnd
import ru.neosvet.vestnewage.data.BookTab
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.helper.BookHelper
import ru.neosvet.vestnewage.helper.DateHelper
import ru.neosvet.vestnewage.loader.BookLoader
import ru.neosvet.vestnewage.loader.MasterLoader
import ru.neosvet.vestnewage.loader.basic.LoadHandlerLite
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.service.LoaderService
import ru.neosvet.vestnewage.storage.JournalStorage
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.*
import ru.neosvet.vestnewage.viewmodel.basic.BookStrings
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.BookState
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
        BookLoader(NeoClient(NeoClient.Type.SECTION, this))
    }
    private val masterLoader: MasterLoader by lazy {
        MasterLoader(this)
    }

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, BookHelper.TAG)
        .putBoolean(Const.FROM_OTKR, isLoadedOtkr)
        .putString(Const.TAB, selectedTab.toString())
        .build()

    override fun init(context: Context) {
        helper = BookHelper().also {
            it.loadDates()
            dPoems = DateUnit.putDays(it.poemsDays)
            dEpistles = DateUnit.putDays(it.epistlesDays)
        }
        strings = BookStrings(
            rnd_epistle = context.getString(R.string.rnd_epistle),
            rnd_poem = context.getString(R.string.rnd_poem),
            rnd_verse = context.getString(R.string.rnd_verse),
            alert_rnd = context.getString(R.string.alert_rnd),
            try_again = context.getString(R.string.try_again),
            from = context.getString(R.string.from)
        )
    }

    override suspend fun defaultState() {
        openList(true, date)
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
                loader.loadDoctrineList()
                openDoctrine()
                if (isRun)
                    loader.loadDoctrinePages(this)
                postState(BasicState.Success)
                return
            }
        }
        postState(BasicState.Success)
        openList(false, date)
    }

    override fun onDestroy() {
        helper?.saveDates(dPoems.timeInDays, dEpistles.timeInDays)
    }

    @SuppressLint("Range")
    fun openList(loadIfNeed: Boolean, newDate: DateUnit?, tab: Int = -1) {
        this.loadIfNeed = loadIfNeed
        if (tab != -1)
            selectedTab = convertTab(tab)
        scope.launch {
            if (selectedTab == BookTab.DOCTRINE) {
                openDoctrine()
                return@launch
            }
            newDate?.let {
                when (selectedTab) {
                    BookTab.POEMS -> dPoems = it
                    BookTab.EPISTLES -> dEpistles = it
                    else -> {}
                }
            }
            if (!existsList(date)) {
                if (loadIfNeed) {
                    postState(BasicState.NotLoaded)
                    reLoad()
                } else
                    postState(BasicState.Empty)
                return@launch
            }
            val list = mutableListOf<BasicItem>()
            val storage = PageStorage()
            var t: String
            var s: String
            var cursor: Cursor
            if (date.timeInDays == DateHelper.MIN_DAYS_NEW_BOOK && isLoadedOtkr.not()) {
                //добавить в список "Предисловие к Толкованиям" /2004/predislovie.html
                storage.open("12.04")
                cursor = storage.getListAll()
                if (cursor.moveToFirst() && cursor.moveToNext()) {
                    t = cursor.getString(cursor.getColumnIndex(Const.TITLE))
                    s = cursor.getString(cursor.getColumnIndex(Const.LINK))
                    list.add(BasicItem(t, s))
                }
                cursor.close()
                storage.close()
            }
            storage.open(date.my)
            cursor = storage.getListAll()
            if (cursor.moveToFirst()) {
                val time = cursor.getLong(cursor.getColumnIndex(Const.TIME))
                if (date.year > 2015) { //списки скаченные с сайта Откровений не надо открывать с фильтром - там и так всё по порядку
                    cursor.close()
                    cursor = storage.getList(isPoemsTab)
                    cursor.moveToFirst()
                } else  // в случае списков с сайта Откровений надо просто перейти к следующей записи
                    cursor.moveToNext()
                val iTitle = cursor.getColumnIndex(Const.TITLE)
                val iLink = cursor.getColumnIndex(Const.LINK)
                do {
                    s = cursor.getString(iLink)
                    t = cursor.getString(iTitle)
                    if (!s.noHasDate && !t.contains(s.date))
                        t += " (" + strings.from + " ${s.date})"
                    list.add(BasicItem(t, s))
                } while (cursor.moveToNext())
                postState(BookState.Primary(time, date, checkPrev(), checkNext(), list))
            }
            cursor.close()
            storage.close()
            if (list.isEmpty()) {
                if (loadIfNeed) {
                    postState(BookState.Primary(0L, date, false, false, list))
                    postState(BasicState.NotLoaded)
                    reLoad()
                } else {
                    postState(BookState.Primary(0L, date, checkPrev(), checkNext(), list))
                    postState(BasicState.Empty)
                }
            }
        }
    }

    private fun checkNext(): Boolean {
        val max = if (isPoemsTab)
            DateUnit.initToday().apply { day = 1 }.timeInDays
        else
            DateHelper.MAX_DAYS_BOOK
        return date.timeInDays < max
    }

    private fun checkPrev(): Boolean {
        val d = date
        val days = d.timeInDays
        val min = if (isPoemsTab) {
            DateHelper.MIN_DAYS_POEMS
        } else if (isLoadedOtkr)
            DateHelper.MIN_DAYS_OLD_BOOK
        else if (days == DateHelper.MIN_DAYS_NEW_BOOK) {
            // доступна для того, чтобы предложить скачать Послания за 2004-2015
            return !LoaderService.isRun
        } else
            DateHelper.MIN_DAYS_NEW_BOOK
        return days > min
    }

    private suspend fun openDoctrine() {
        val storage = PageStorage()
        storage.open(DataBase.DOCTRINE)
        val cursor = storage.getListAll()
        cursor.moveToFirst()
        if (cursor.count == 1) {
            cursor.close()
            storage.close()
            postState(BookState.Book(Urls.DoctrineSite, listOf()))
            reLoad()
            return
        }
        val iTitle = cursor.getColumnIndex(Const.TITLE)
        val iLink = cursor.getColumnIndex(Const.LINK)
        val list = mutableListOf<BasicItem>()
        while (cursor.moveToNext()) {
            val title = cursor.getString(iTitle)
            val link = cursor.getString(iLink)
            list.add(BasicItem(title, link))
        }
        postState(BookState.Book(Urls.DoctrineSite, list))
        cursor.close()
        storage.close()
    }

    private fun existsList(d: DateUnit): Boolean {
        val storage = PageStorage()
        storage.open(d.my)
        val cursor = storage.getLinks()
        var s: String
        if (cursor.moveToFirst()) {
            // первую запись пропускаем, т.к. там дата изменения списка
            while (cursor.moveToNext()) {
                s = cursor.getString(0)
                if (s.isPoem == isPoemsTab) {
                    cursor.close()
                    storage.close()
                    return true
                }
            }
        }
        cursor.close()
        storage.close()
        return false
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
                val f = Lib.getFileDB(d.my)
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
                    title = strings.rnd_poem
                    curTitle = storage.getList(true)
                }

                BookRnd.EPISTLE -> {
                    title = strings.rnd_epistle
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
                postState(BasicState.Message(strings.alert_rnd))
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
                        s = Lib.withOutTags(curPar.getString(0))
                    } else {
                        s = ""
                        n = -1
                    }
                } else s = ""
                curPar.close()
                if (s == "") { //случайный стих не найден
                    postState(BasicState.Message(strings.alert_rnd))
                    title = strings.rnd_verse
                }
            } else  // случайный катрен или послание
                n = -1
            //выводим на экран:
            val link: String? = curTitle.getString(curTitle.getColumnIndex(Const.LINK))
            var msg: String
            msg = if (link == null) strings.try_again
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

    fun setArgument(tab: Int) {
        selectedTab = convertTab(tab)
    }

    private fun convertTab(tab: Int) = when (tab) {
        BookTab.EPISTLES.value -> BookTab.EPISTLES
        BookTab.DOCTRINE.value -> BookTab.DOCTRINE
        else -> BookTab.POEMS
    }
}