package ru.neosvet.vestnewage.viewmodel

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import androidx.work.Data
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.helper.BookHelper
import ru.neosvet.vestnewage.loader.BookLoader
import ru.neosvet.vestnewage.loader.MasterLoader
import ru.neosvet.vestnewage.loader.basic.LoadHandlerLite
import ru.neosvet.vestnewage.service.LoaderService
import ru.neosvet.vestnewage.storage.JournalStorage
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.isPoem
import ru.neosvet.vestnewage.viewmodel.basic.BookStrings
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import java.util.*

class BookToiler : NeoToiler(), LoadHandlerLite {
    companion object {
        const val TAB_POEMS = 0
        const val TAB_EPISTLES = 1
        const val TAB_DOCTRINE = 2
    }

    enum class RndType {
        POEM, EPISTLE, VERSE
    }

    var selectedTab: Int = TAB_POEMS
    val isPoemsTab: Boolean
        get() = selectedTab == TAB_POEMS
    val isDoctrineTab: Boolean
        get() = selectedTab == TAB_DOCTRINE
    private lateinit var dPoems: DateUnit
    private lateinit var dEpistles: DateUnit
    private lateinit var strings: BookStrings
    var helper: BookHelper? = null
        private set
    private var isLoadedOtkr: Boolean = false
    var date: DateUnit
        get() = if (isPoemsTab) dPoems else dEpistles
        set(value) {
            if (isPoemsTab) dPoems = value
            else dEpistles = value
        }
    private var loader: BookLoader? = null
    private var masterLoader: MasterLoader? = null

    fun init(context: Context) {
        helper = BookHelper().also {
            isLoadedOtkr = it.isLoadedOtkr()
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
            from = context.getString(R.string.from),
            month_is_empty = context.getString(R.string.month_is_empty)
        )
        openList(true)
    }

    override suspend fun doLoad() {
        val loader = if (loader == null) {
            loader = BookLoader(this)
            loader!!
        } else loader!!
        when (selectedTab) {
            TAB_POEMS -> loader.loadPoemsList(dPoems.year)
            TAB_EPISTLES -> if (dEpistles.year < 2016) {
                if (masterLoader == null)
                    masterLoader = MasterLoader(this)
                masterLoader?.loadMonth(dEpistles.month, dEpistles.year)
            } else loader.loadEpistlesList()
            TAB_DOCTRINE -> {
                loader.loadDoctrineList()
                openDoctrine()
                loader.loadDoctrinePages()
                postState(NeoState.Success)
                return
            }
        }
        postState(NeoState.Success)
        openList(false)
    }

    override fun cancel() {
        masterLoader?.cancel()
        loader?.cancel()
        super.cancel()
    }

    override fun onDestroy() {
        helper?.saveDates(dPoems.timeInDays, dEpistles.timeInDays)
    }

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, BookHelper.TAG)
        .putBoolean(Const.FROM_OTKR, isLoadedOtkr)
        .putBoolean(Const.POEMS, isPoemsTab)
        .build()

    @SuppressLint("Range")
    fun openList(loadIfNeed: Boolean) {
        this.loadIfNeed = loadIfNeed
        scope.launch {
            if (isDoctrineTab) {
                openDoctrine()
                return@launch
            }
            val d = date
            if (!existsList(d)) {
                postState(NeoState.LongValue(0))
                reLoad()
                return@launch
            }
            val list = mutableListOf<ListItem>()
            val calendar = d.calendarString
            val storage = PageStorage()
            var t: String
            var s: String
            var cursor: Cursor
            if (d.timeInDays == BookHelper.MIN_DAYS_NEW_BOOK && isLoadedOtkr.not()) {
                //добавить в список "Предисловие к Толкованиям" /2004/predislovie.html
                storage.open("12.04")
                cursor = storage.getListAll()
                if (cursor.moveToFirst() && cursor.moveToNext()) {
                    t = cursor.getString(cursor.getColumnIndex(Const.TITLE))
                    s = cursor.getString(cursor.getColumnIndex(Const.LINK))
                    list.add(ListItem(t, s))
                }
                cursor.close()
                storage.close()
            }
            storage.open(d.my)
            cursor = storage.getListAll()
            val dModList: DateUnit
            if (cursor.moveToFirst()) {
                val time = cursor.getLong(cursor.getColumnIndex(Const.TIME))
                postState(NeoState.LongValue(time))
                dModList = DateUnit.putMills(time)
                if (d.year > 2015) { //списки скаченные с сайта Откровений не надо открывать с фильтром - там и так всё по порядку
                    cursor.close()
                    cursor = storage.getList(isPoemsTab)
                    cursor.moveToFirst()
                } else  // в случае списков с сайта Откровений надо просто перейти к следующей записи
                    cursor.moveToNext()
                val iTitle = cursor.getColumnIndex(Const.TITLE)
                val iLink = cursor.getColumnIndex(Const.LINK)
                do {
                    s = cursor.getString(iLink)
                    if (s.contains("2004") || s.contains("pred"))
                        t = cursor.getString(iTitle)
                    else {
                        t = s.substring(s.lastIndexOf("/") + 1, s.lastIndexOf("."))
                        if (t.contains("_")) t = t.substring(0, t.indexOf("_"))
                        if (t.contains("#")) t = t.substring(0, t.indexOf("#"))
                        t = cursor.getString(iTitle) + " (" + strings.from + " $t)"
                    }
                    list.add(ListItem(t, s))
                } while (cursor.moveToNext())
            } else dModList = d
            cursor.close()
            storage.close()
            if (list.isNotEmpty()) {
                postState(NeoState.Book(calendar, checkPrev(), checkNext(), list))
                return@launch
            }
            val today = DateUnit.initToday()
            if (loadIfNeed.not() || dModList.month == today.month && dModList.year == today.year)
                postState(NeoState.Message(strings.month_is_empty))
            else reLoad()
        }
    }

    private fun checkNext(): Boolean {
        val max = if (isPoemsTab)
            DateUnit.initToday().apply { day = 1 }.timeInDays
        else
            BookHelper.MAX_DAYS_BOOK
        return date.timeInDays < max
    }

    private fun checkPrev(): Boolean {
        val d = date
        val days = d.timeInDays
        val min = if (isPoemsTab) {
            BookHelper.MIN_DAYS_POEMS
        } else if (days == BookHelper.MIN_DAYS_NEW_BOOK && isLoadedOtkr.not()) {
            // доступна для того, чтобы предложить скачать Послания за 2004-2015
            return !LoaderService.isRun
        } else if (isLoadedOtkr)
            BookHelper.MIN_DAYS_OLD_BOOK
        else
            BookHelper.MIN_DAYS_NEW_BOOK
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
            postState(NeoState.LongValue(0))
            reLoad()
            return
        }
        val iTitle = cursor.getColumnIndex(Const.TITLE)
        val iLink = cursor.getColumnIndex(Const.LINK)
        val list = mutableListOf<ListItem>()
        while (cursor.moveToNext()) {
            val title = cursor.getString(iTitle)
            val link = cursor.getString(iLink)
            list.add(ListItem(title, link))
        }
        postState(NeoState.ListValue(list))
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
    fun getRnd(type: RndType) {
        scope.launch {
            //Определяем диапозон дат:
            val d = DateUnit.initToday()
            var m: Int
            var y: Int
            var maxM = d.month
            var maxY = d.year - 2000
            if (type == RndType.POEM) {
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
                if (type == RndType.EPISTLE) {
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
                RndType.POEM -> {
                    title = strings.rnd_poem
                    curTitle = storage.getList(true)
                }
                RndType.EPISTLE -> {
                    title = strings.rnd_epistle
                    curTitle = storage.getList(false)
                }
                RndType.VERSE ->
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
                postState(NeoState.Message(strings.alert_rnd))
                return@launch
            }
            //если случайный текст найден
            var s = ""
            if (type == RndType.VERSE) { //случайных стих
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
                    postState(NeoState.Message(strings.alert_rnd))
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
                NeoState.Rnd(
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
        setState(NeoState.Progress(value))
    }
}