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
        const val TAB_KATREN = 0
        //const val TAB_POSLANYA = 1
    }

    enum class RndType {
        KAT, POS, STIH
    }

    var selectedTab: Int = TAB_KATREN
    val isKatrenTab: Boolean
        get() = selectedTab == TAB_KATREN
    private lateinit var dKatren: DateUnit
    private lateinit var dPoslanie: DateUnit
    private lateinit var strings: BookStrings
    var helper: BookHelper? = null
        private set
    var isLoadedOtkr: Boolean = false
    var date: DateUnit
        get() = if (isKatrenTab) dKatren else dPoslanie
        set(value) {
            if (isKatrenTab) dKatren = value
            else dPoslanie = value
        }
    private var loader: BookLoader? = null

    fun init(context: Context) {
        helper = BookHelper().also {
            isLoadedOtkr = it.isLoadedOtkr()
            it.loadDates()
            dKatren = DateUnit.putDays(it.katrenDays)
            dPoslanie = DateUnit.putDays(it.poslaniyaDays)
        }
        if (isLoadedOtkr.not() && dPoslanie.year < 2016)
            dPoslanie = DateUnit.putYearMonth(2016, 1)
        strings = BookStrings(
            rnd_pos = context.getString(R.string.rnd_pos),
            rnd_kat = context.getString(R.string.rnd_kat),
            rnd_stih = context.getString(R.string.rnd_stih),
            alert_rnd = context.getString(R.string.alert_rnd),
            try_again = context.getString(R.string.try_again),
            from = context.getString(R.string.from),
            month_is_empty = context.getString(R.string.month_is_empty)
        )
        openList(true)
    }

    override suspend fun doLoad() {
        loader = BookLoader(this)
        if (isKatrenTab) loader?.loadPoemsList(2016)?.let {
            dKatren = DateUnit.parse(it)
        } else if (isLoadedOtkr) loader?.loadAllPoslaniya()?.let {
            dPoslanie = DateUnit.parse(it)
        } else loader?.loadNewPoslaniya()?.let {
            dPoslanie = DateUnit.parse(it)
        }
        openList(false)
    }

    override fun cancel() {
        loader?.cancel()
        super.cancel()
    }

    override fun onDestroy() {
        helper?.saveDates(dKatren.timeInDays, dPoslanie.timeInDays)
    }

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, BookHelper.TAG)
        .putBoolean(Const.FROM_OTKR, isLoadedOtkr)
        .putBoolean(Const.KATRENY, isKatrenTab)
        .build()

    @SuppressLint("Range")
    fun openList(loadIfNeed: Boolean) {
        this.loadIfNeed = loadIfNeed
        scope.launch {
            val d = date
            if (!existsList(d)) {
                reLoad()
                return@launch
            }
            val list = mutableListOf<ListItem>()
            val calendar = d.calendarString
            val prev: Boolean
            if (d.month == 1 && d.year == 2016 && isLoadedOtkr.not()) {
                // доступна для того, чтобы предложить скачать Послания за 2004-2015
                prev = !LoaderService.isRun
                d.changeMonth(1)
            } else {
                d.changeMonth(-1)
                prev = existsList(d)
                d.changeMonth(2)
            }
            val next = existsList(d)
            d.changeMonth(-1)
            val storage = PageStorage()
            var t: String
            var s: String
            var cursor: Cursor
            if (d.month == 1 && d.year == 2016 && isLoadedOtkr.not()) {
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
                mstate.postValue(NeoState.LongState(time))
                waitPost()
                dModList = DateUnit.putMills(time)
                if (d.year > 2015) { //списки скаченные с сайта Откровений не надо открывать с фильтром - там и так всё по порядку
                    cursor.close()
                    cursor = storage.getList(isKatrenTab)
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
                mstate.postValue(NeoState.Book(calendar, prev, next, list))
                return@launch
            }
            val today = DateUnit.initToday()
            if (loadIfNeed.not() || dModList.month == today.month && dModList.year == today.year)
                mstate.postValue(NeoState.Message(strings.month_is_empty))
            else reLoad()
        }
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
                if (s.isPoem == isKatrenTab) {
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
        //Определяем диапозон дат:
        val d = DateUnit.initToday()
        var m: Int
        var y: Int
        var maxM = d.month
        var maxY = d.year - 2000
        if (type == RndType.KAT) {
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
            if (type == RndType.POS) {
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
            RndType.KAT -> {
                title = strings.rnd_kat
                curTitle = storage.getList(true)
            }
            RndType.POS -> {
                title = strings.rnd_pos
                curTitle = storage.getList(false)
            }
            RndType.STIH ->
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
            mstate.postValue(NeoState.Message(strings.alert_rnd))
            return
        }
        //если случайный текст найден
        var s = ""
        if (type == RndType.STIH) { //случайных стих
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
                Lib.showToast(strings.alert_rnd)
                title = strings.rnd_stih
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
        mstate.postValue(
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
            return
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

    override fun postPercent(value: Int) {
        mstate.postValue(NeoState.Progress(value))
    }
}