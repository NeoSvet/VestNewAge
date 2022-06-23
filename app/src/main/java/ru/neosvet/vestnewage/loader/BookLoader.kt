package ru.neosvet.vestnewage.loader

import android.content.ContentValues
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.MyException
import ru.neosvet.vestnewage.helper.BookHelper
import ru.neosvet.vestnewage.loader.basic.LoadHandler
import ru.neosvet.vestnewage.loader.basic.LoadHandlerLite
import ru.neosvet.vestnewage.loader.basic.Loader
import ru.neosvet.vestnewage.loader.page.PageParser
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.percent
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class BookLoader : Loader {
    companion object {
        private const val SIZE_EMPTY_BASE = 24576L
    }

    private val handler: LoadHandler?
    private val handlerLite: LoadHandlerLite?

    constructor(handler: LoadHandler) {
        this.handler = handler
        handlerLite = null
    }

    constructor(handler: LoadHandlerLite) {
        this.handler = null
        handlerLite = handler
    }

    private val title = mutableListOf<String>()
    private val links = mutableListOf<String>()
    private var cur = 0
    private var max = 0
    private var isRun = true

    override fun cancel() {
        isRun = false
    }

    fun loadOldEpistles() { //загрузка Посланий за 2004-2015
        isRun = true
        handler?.setMax(137) //август 2004 - декабрь 2015
        loadListUcoz(UcozType.OLD)?.let {
            handler?.postMessage(it)
        }
        val book = BookHelper()
        book.setLoadedOtkr(true)
    }

    fun loadPoemsList(startYear: Int): String? {
        isRun = true
        val d = DateUnit.initToday()
        val finalYear = d.year
        max = (finalYear - startYear) * 12 + d.month - 1
        cur = 0
        var s: String? = null
        for (i in startYear..finalYear) {
            s = if (NeoClient.isMainSite())
                loadList(NeoClient.SITE + Const.PRINT + Const.POEMS + "/" + i + Const.HTML)
            else
                loadList(NeoClient.SITE2 + Const.PRINT + i + Const.HTML)
            if (isRun.not()) break
            handler?.upProg()
        }
        return s
    }

    fun loadAllEpistles(): String? {
        isRun = true
        cur = 0
        max = 146 //август 2004 - сентябрь 2016
        loadListUcoz(UcozType.OLD) //до 2016 года
        return loadNewEpistles() //за 2016 год
    }

    fun loadNewEpistles(): String? {
        isRun = true
        if (NeoClient.isMainSite())
            return loadList(NeoClient.SITE + Const.PRINT + "tolkovaniya" + Const.HTML)
        throw MyException(App.context.getString(R.string.site_unavailable))
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
        val list = mutableListOf<String>()
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
        var d: DateUnit
        for (item: String in list) {
            handlerLite?.let {
                it.postPercent(cur.percent(max))
                cur++
            } ?: handler?.let {
                d = DateUnit.parse(item)
                it.postMessage(d.monthString + " " + d.year)
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

    private fun loadList(url: String): String? {
        val page = PageParser()
        if (NeoClient.isMainSite())
            page.load(url, "page-title")
        else
            page.load(url, "<h2>")
        var a: String?
        var s: String
        var date1: String? = null
        var date2: String
        do {
            a = page.link
            while (a == null && page.nextItem != null) {
                a = page.link
            }
            if (a == null) break
            if (a.length < 19) continue
            date2 = PageStorage.getDatePage(a)
            if (date1 == null)
                date1 = date2
            else if (date2 != date1) {
                saveData(date1)
                if (max > 0) {
                    handlerLite?.postPercent(cur.percent(max))
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