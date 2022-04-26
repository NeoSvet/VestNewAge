package ru.neosvet.vestnewage.loader

import android.content.ContentValues
import ru.neosvet.html.PageParser
import ru.neosvet.utils.*
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.helpers.BookHelper
import ru.neosvet.vestnewage.helpers.DateHelper
import ru.neosvet.vestnewage.helpers.ProgressHelper
import ru.neosvet.vestnewage.service.LoaderService
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.storage.PageStorage.Companion.getDatePage
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class BookLoader {
    interface Handler {
        fun setMax(value: Int)
        fun upProg()
        fun postMessage(value: String)
    }

    interface HandlerLite {
        fun postPercent(value: Int)
    }

    companion object {
        private const val SIZE_EMPTY_BASE = 24576L
    }

    private val handler: Handler?
    private val handlerLite: HandlerLite?

    constructor(handler: Handler) {
        this.handler = handler
        handlerLite = null
    }

    constructor(handler: HandlerLite) {
        this.handler = null
        handlerLite = handler
    }

    private val title = mutableListOf<String>()
    private val links = mutableListOf<String>()
    private var cur = 0
    private var max = 0
    private var isRun = true

    fun cancel() {
        isRun = false
    }

    fun loadOtrk() { //загрузка Посланий за 2004-2015
        isRun = true
        handler?.setMax(137) //август 2004 - декабрь 2015
        loadListUcoz(UcozType.OLD)?.let {
            handler?.postMessage(it)
        }
        val book = BookHelper()
        book.setLoadedOtkr(true)
        LoaderService.postCommand(
            LoaderService.STOP_WITH_NOTIF, null)
    }

    fun loadPoems(all: Boolean): String? {
        isRun = true
        val d = DateHelper.initToday()
        val y = d.year
        var i = if (all) 2016 else y - 1
        max = (y - i) * 12 + d.month - 1
        cur = 0
        var s: String? = null
        while (i <= y) {
            s = if (NeoClient.isMainSite())
                loadListBook(NeoClient.SITE + Const.PRINT + Const.POEMS + "/" + i + Const.HTML)
            else
                loadListBook(NeoClient.SITE2 + Const.PRINT + i + Const.HTML)
            if (isRun.not()) break
            i++
        }
        return s
    }

    fun loadPoslaniya(fromOtkr: Boolean): String? {
        isRun = true
        cur = 0
        if (fromOtkr) { //все Послания
            max = 146 //август 2004 - сентябрь 2016
            loadListUcoz(UcozType.OLD) //обновление старых Посланий
        } else //только новые Послания
            max = 9 //январь-сентябрь 2016
        return loadTolkovaniya()
    }

    fun loadTolkovaniya(): String? {
        isRun = true
        if (NeoClient.isMainSite())
            return loadListBook(NeoClient.SITE + Const.PRINT + "tolkovaniya" + Const.HTML)
        throw MyException(App.context.getString(R.string.site_not_available))
    }

    private enum class UcozType {
        OLD,  //август 2004 - декабрь 2015
        PART1,  //январь 2016 - декабрь 2020
        PART2 //январь 2021 - ...
    }

    private fun loadListUcoz(type: UcozType): String? {
        var name: String? = ""
        var url = "http://neosvet.ucoz.ru/databases_vna" +
                when (type) {
                    UcozType.OLD -> "/list.txt"
                    UcozType.PART1 -> "/list_new.txt"
                    UcozType.PART2 -> "2/list.txt"
                }
        var f: File
        var l: Long
        //list format:
        //01.05 delete [time] - при необходимости список обновить
        //02.05 [length] - проверка целостности
        var br = BufferedReader(InputStreamReader(NeoClient.getStream(url)), 1000)
        val list: MutableList<String> = java.util.ArrayList()
        var s: String? = br.readLine()
        while (s != null) {
            if (isRun.not()) {
                br.close()
                return name
            }
            name = s.substring(0, s.indexOf(" "))
            f = Lib.getFileDB(name)
            if (f.exists()) {
                l = s.substring(s.lastIndexOf(" ") + 1).toLong()
                if (s.contains("delete")) {
                    if (f.lastModified() < l) {
                        list.add(name)
                        f.delete()
                    }
                } else if (l != f.length()) {
                    list.add(name)
                    if (f.length() != SIZE_EMPTY_BASE) f.delete()
                }
            } else {
                list.add(name)
            }
            s = br.readLine()
        }
        br.close()
        url = url.substring(0, url.lastIndexOf("/") + 1)
        val storage = PageStorage()
        var isTitle: Boolean
        val ids = HashMap<String, Int>()
        var n: Int
        var id: Int
        var v: String
        val time = System.currentTimeMillis()
        var d: DateHelper
        for (item: String in list) {
            if (max > 0) {
                handlerLite?.postPercent(getPercent())
                cur++
            } else {
                d = DateHelper.parse(item)
                handler?.postMessage(d.monthString + " " + d.year)
            }
            storage.open(item)
            isTitle = true
            br = BufferedReader(
                InputStreamReader(NeoClient.getStream(url + item), Const.ENCODING),
                1000
            )
            n = 2
            s = br.readLine()
            while (s != null) {
                if (s == Const.AND) {
                    isTitle = false
                    s = br.readLine()
                }
                v = br.readLine()
                s?.let {
                    if (isTitle) {
                        id = storage.getPageId(it)
                        if (id == -1)
                            id = storage.insertTitle(getRow(it, v, time)).toInt()
                        else
                            storage.updateTitle(it, getRow(it, v, time))
                        ids[n.toString()] = id
                        n++
                    } else ids[it]?.let { id ->
                        storage.insertParagraph(getRow(id, v))
                    }
                }
                s = br.readLine()
            }
            br.close()
            storage.close()
            name = item
            handler?.upProg()
            if (isRun.not()) return name
        }
        return name
    }

    private fun getPercent(): Int =
        ProgressHelper.getPercent(cur.toFloat(), max.toFloat())

    private fun getRow(link: String, title: String, time: Long): ContentValues {
        val row = ContentValues()
        row.put(Const.LINK, link)
        row.put(Const.TITLE, title)
        row.put(Const.TIME, time)
        return row
    }

    private fun getRow(id: Int, par: String): ContentValues {
        val row = ContentValues()
        row.put(DataBase.ID, id)
        row.put(DataBase.PARAGRAPH, par)
        return row
    }

    private fun loadListBook(url: String): String? {
        val page = PageParser()
        if (NeoClient.isMainSite())
            page.load(url, "page-title")
        else
            page.load(url, "<h2>")
        var a: String?
        var s: String
        var date1: String? = null
        var date2: String
        page.firstElem
        do {
            a = page.link
            while (a == null && page.nextItem != null) {
                a = page.link
            }
            if (a == null) break
            if (a.length < 19) continue
            date2 = getDatePage(a)
            if (date1 == null)
                date1 = date2
            else if (date2 != date1) {
                saveData(date1)
                if (max > 0) {
                    handlerLite?.postPercent(getPercent())
                    cur++
                } else handler?.upProg()
                date1 = date2
            }
            s = page.text
            if (s.contains("(")) //poems
                s = s.substring(0, s.indexOf(" ("))
            title.add(s)
            links.add(a.substring(1))
        } while (page.nextItem != null)
        page.clear()
        date1?.let { saveData(it) }
        return date1
    }

    private fun saveData(date: String) {
        if (title.size > 0) {
            val storage = PageStorage()
            storage.open(date)
            var row = ContentValues()
            row.put(Const.TIME, System.currentTimeMillis())
            if (!storage.updateTitle(1, row)) {
                storage.insertTitle(row)
            }
            for (i in 0 until title.size) {
                row = ContentValues()
                row.put(Const.TITLE, title[i])
                // пытаемся обновить запись:
                if (!storage.updateTitle(links[i], row)) {
                    // обновить не получилось, добавляем:
                    row.put(Const.LINK, links[i])
                    storage.insertTitle(row)
                }
            }
            storage.close()
            title.clear()
            links.clear()
        }
    }

    fun loadAllUcoz() {
        isRun = true
        loadListUcoz(UcozType.PART1)
        loadListUcoz(UcozType.PART2)
    }
}