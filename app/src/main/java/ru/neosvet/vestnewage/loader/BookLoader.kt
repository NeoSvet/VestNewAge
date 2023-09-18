package ru.neosvet.vestnewage.loader

import android.content.ContentValues
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.loader.basic.LoadHandlerLite
import ru.neosvet.vestnewage.loader.basic.Loader
import ru.neosvet.vestnewage.loader.page.PageParser
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.isPoem
import ru.neosvet.vestnewage.utils.percent
import java.io.BufferedReader
import java.io.InputStreamReader

class BookLoader(private val client: NeoClient) : Loader {
    private val title = mutableListOf<String>()
    private val links = mutableListOf<String>()
    private var isRun = true
    private val clientDoctrine: NeoClient by lazy {
        NeoClient(NeoClient.Type.MAIN)
    }

    override fun load() {
        loadYearList(DateUnit.initToday().year)
    }

    override fun cancel() {
        isRun = false
    }

    fun loadYearList(year: Int) {
        if (year == 2016)
            loadEpistlesList()
        if (isRun.not()) return
        loadList(Urls.getPoems(year))
    }

    fun loadPoemsList(year: Int) {
        isRun = true
        loadList(Urls.getPoems(year))
    }

    fun loadEpistlesList() {
        isRun = true
        loadList(Urls.Epistles)
    }

    private fun loadList(url: String) {
        val page = PageParser(client)
        page.load(url, "page-title")
        var a: String?
        var s: String
        var date1: String? = null
        var date2: String
        do {
            a = page.link
            while (a == null && page.nextItem != null)
                a = page.link
            if (a == null) break
            if (a.length < 19) continue
            date2 = PageStorage.getDatePage(a)
            if (date1 == null)
                date1 = date2
            else if (date2 != date1) {
                saveData(date1)
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
        val stream = InputStreamReader(
            clientDoctrine.getStream("${Urls.DoctrineBase}list.txt"),
            Const.ENCODING
        )
        val br = BufferedReader(stream, 1000)
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
            if (isRun.not()) break
            link = br.readLine()
        }
        br.close()
        storage.close()
    }

    fun loadDoctrinePages(handler: LoadHandlerLite?) {
        isRun = true
        val storage = PageStorage()
        storage.open(DataBase.DOCTRINE)
        var s: String?
        var time: Long
        val cursor = storage.getListAll()
        val max = cursor.count - 1
        var cur = 0
        cursor.moveToFirst()
        val host = Urls.DoctrineBase
        val iId = cursor.getColumnIndex(DataBase.ID)
        val iLink = cursor.getColumnIndex(Const.LINK)
        val iTime = cursor.getColumnIndex(Const.TIME)
        while (cursor.moveToNext() && isRun) {
            val id = cursor.getInt(iId)
            val link = cursor.getString(iLink)
            time = cursor.getLong(iTime)
            s = link.substring(Const.DOCTRINE.length) //pages
            val stream = clientDoctrine.getStream("$host$s.txt")
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
            handler?.let {
                cur++
                it.postPercent(cur.percent(max))
            }
        }
        cursor.close()
        storage.close()
    }
}