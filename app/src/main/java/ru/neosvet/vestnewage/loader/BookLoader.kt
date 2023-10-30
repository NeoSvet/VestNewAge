package ru.neosvet.vestnewage.loader

import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.loader.basic.LoadHandlerLite
import ru.neosvet.vestnewage.loader.basic.Loader
import ru.neosvet.vestnewage.loader.page.PageParser
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.DataBase
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.isDoctrineBook
import ru.neosvet.vestnewage.utils.isPoem
import ru.neosvet.vestnewage.utils.percent
import java.io.BufferedReader
import java.io.InputStreamReader

class BookLoader(private val client: NeoClient) : Loader {
    private val title = mutableListOf<String>()
    private val links = mutableListOf<String>()
    private var isRun = true
    private val clientBook: NeoClient by lazy {
        NeoClient()
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
        storage.updateTime()
        for (i in 0 until title.size)
            storage.putTitle(title[i], links[i])
        storage.close()
        title.clear()
        links.clear()
    }

    fun loadBookList(isRus: Boolean) {
        isRun = true
        //list format:
        //line 1: pages
        //line 2: title
        val m = if (isRus) arrayOf(Urls.HolyRusBase, DataBase.HOLY_RUS, Const.HOLY_RUS)
        else arrayOf(Urls.DoctrineBase, DataBase.DOCTRINE, Const.DOCTRINE)
        val stream = InputStreamReader(
            clientBook.getStream("${m[0]}list.txt"),
            Const.ENCODING
        )
        val br = BufferedReader(stream, 1000)
        val storage = PageStorage()
        storage.open(m[1])
        var link: String? = br.readLine()
        while (link != null) {
            val title = br.readLine()
            link = m[2] + link
            storage.putTitle(title, link)
            if (isRun.not()) break
            link = br.readLine()
        }
        br.close()
        storage.close()
    }

    fun loadBook(isRus: Boolean, handler: LoadHandlerLite?) {
        isRun = true
        val m = if (isRus) arrayOf(Urls.HolyRusBase, DataBase.HOLY_RUS, Const.HOLY_RUS)
        else arrayOf(Urls.DoctrineBase, DataBase.DOCTRINE, Const.DOCTRINE)
        val storage = PageStorage()
        storage.open(m[1])
        val cursor = storage.getListAll()
        if (cursor.moveToFirst()) {
            var s: String?
            var time: Long
            val max = cursor.count - 1
            var cur = 0
            val iId = cursor.getColumnIndex(DataBase.ID)
            val iLink = cursor.getColumnIndex(Const.LINK)
            val iTime = cursor.getColumnIndex(Const.TIME)
            while (cursor.moveToNext() && isRun) {
                val link = cursor.getString(iLink)
                if (!isRus && !link.isDoctrineBook) continue
                val id = cursor.getInt(iId)
                time = cursor.getLong(iTime)
                s = link.substring(m[2].length) //pages
                val stream = clientBook.getStream("${m[0]}$s.txt")
                val br = BufferedReader(InputStreamReader(stream, Const.ENCODING), 1000)
                s = br.readLine() //time
                if (time != s.toLong().apply { time = this }) {
                    storage.deleteParagraphs(id)
                    s = br.readLine()
                    while (s != null) {
                        storage.insertParagraph(id, s)
                        s = br.readLine()
                    }
                    storage.updateTime(link, time)
                }
                br.close()
                handler?.let {
                    cur++
                    it.postPercent(cur.percent(max))
                }
            }
        }
        cursor.close()
        storage.close()
    }
}