package ru.neosvet.vestnewage.loader

import android.content.ContentValues
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.MyException
import ru.neosvet.vestnewage.loader.basic.LoadHandlerLite
import ru.neosvet.vestnewage.loader.basic.Loader
import ru.neosvet.vestnewage.loader.page.PageParser
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.NetConst
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.isPoem
import ru.neosvet.vestnewage.utils.percent
import java.io.BufferedReader
import java.io.InputStreamReader

class BookLoader : Loader {
    private val handlerLite: LoadHandlerLite?

    constructor() {
        handlerLite = null
    }

    constructor(handler: LoadHandlerLite) {
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

    fun loadYearList(year: Int) {
        if (year == 2016)
            loadEpistlesList()
        if (isRun.not()) return
        if (NeoClient.isMainSite())
            loadList(NetConst.SITE + Const.PRINT + Const.POEMS + "/" + year + Const.HTML)
        else
            loadList(NetConst.SITE2 + Const.PRINT + year + Const.HTML)
    }

    fun loadPoemsList(year: Int) {
        isRun = true
        cur = 0
        max = 12
        if (NeoClient.isMainSite())
            loadList(NetConst.SITE + Const.PRINT + Const.POEMS + "/" + year + Const.HTML)
        else
            loadList(NetConst.SITE2 + Const.PRINT + year + Const.HTML)
    }

    fun loadEpistlesList() {
        isRun = true
        if (NeoClient.isMainSite())
            loadList(NetConst.SITE + Const.PRINT + "tolkovaniya" + Const.HTML)
        else
            throw MyException(App.context.getString(R.string.site_unavailable))
    }

    private fun loadList(url: String) {
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
                }
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
    }

    private fun saveData(date: String) {
        if (title.isEmpty()) return
        val storage = PageStorage()
        storage.open(date)
        //clear storage:
        val isPoem = links[0].isPoem
        val list = storage.getLinksList()
            .filter { link -> link.isPoem == isPoem && !links.contains(link) }
        if (list.isNotEmpty()) storage.deletePages(list)
        //fill in storage:
        var row = ContentValues()
        row.put(Const.TIME, System.currentTimeMillis())
        if (!storage.updateTitle(1, row))
            storage.insertTitle(row)
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

    fun loadDoctrineList() {
        isRun = true
        //list format:
        //line 1: pages
        //line 2: title
        val url = NetConst.DOCTRINE_BASE + "list.txt"
        val br = BufferedReader(InputStreamReader(NeoClient.getStream(url), Const.ENCODING), 1000)
        val storage = PageStorage()
        storage.open(DataBase.DOCTRINE)
        var link: String? = br.readLine()
        while (link != null) {
            val title = br.readLine()
            link = Const.DOCTRINE + link
            val row = ContentValues()
            row.put(Const.LINK, link)
            row.put(Const.TITLE, title)
            row.put(Const.TIME, 0)
            if (storage.getPageId(link) == -1)
                storage.insertTitle(row).toInt()
            else
                storage.updateTitle(link, row)
            if (isRun.not())
                break
            link = br.readLine()
        }
        br.close()
        storage.close()
    }

    fun loadDoctrinePages() {
        isRun = true
        val storage = PageStorage()
        storage.open(DataBase.DOCTRINE)
        var s: String?
        var time: Long
        val cursor = storage.getListAll()
        max = cursor.count - 1
        cur = 0
        cursor.moveToFirst()
        val iId = cursor.getColumnIndex(DataBase.ID)
        val iLink = cursor.getColumnIndex(Const.LINK)
        val iTime = cursor.getColumnIndex(Const.TIME)
        while (cursor.moveToNext()) {
            val id = cursor.getInt(iId)
            val link = cursor.getString(iLink)
            time = cursor.getLong(iTime)
            s = link.substring(Const.DOCTRINE.length) //pages
            val stream = NeoClient.getStream(NetConst.DOCTRINE_BASE + "$s.p")
            val br = BufferedReader(InputStreamReader(stream, Const.ENCODING), 1000)
            s = br.readLine() //time
            if (s != null && s.toLong() != time) {
                time = s.toLong()
                storage.deleteParagraphs(id)
                s = br.readLine()
                while (s != null) {
                    val row = ContentValues()
                    row.put(DataBase.ID, id)
                    row.put(DataBase.PARAGRAPH, s)
                    storage.insertParagraph(row)
                    s = br.readLine()
                }
                val row = ContentValues()
                row.put(Const.TIME, time)
                storage.updateTitle(link, row)
            }
            br.close()
            handlerLite?.let {
                cur++
                it.postPercent(cur.percent(max))
            }
        }
        cursor.close()
        storage.close()
    }
}