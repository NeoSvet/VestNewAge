package ru.neosvet.vestnewage.loader

import ru.neosvet.vestnewage.data.BookTab
import ru.neosvet.vestnewage.data.Books
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
        page.load(url, "")
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
                s = s.take(s.indexOf(" ("))
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

    fun loadBookList(book: BookTab) {
        //DOCTRINE(2), HOLY_RUS(3), WORLD_AFTER_WAR(4)
        isRun = true
        //list format:
        //line 1: pages
        //line 2: title
        val stream = InputStreamReader(
            client.getStream("${Books.baseUrl(book)}list.txt"),
            Const.ENCODING
        )
        val br = BufferedReader(stream, 1000)
        val storage = PageStorage()
        storage.open(Books.baseName(book))
        val prefix = Books.Prefix(book)
        var link: String? = br.readLine()
        while (link != null && isRun) {
            storage.putTitle(br.readLine(), prefix + link)
            link = br.readLine()
        }
        br.close()
        storage.close()
    }

    fun loadBook(book: BookTab, handler: LoadHandlerLite) {
        //DOCTRINE(2), HOLY_RUS(3), WORLD_AFTER_WAR(4)
        isRun = true
        val storage = PageStorage()
        storage.open(Books.baseName(book))
        var time: Long
        var cur = 0
        val prefix = Books.Prefix(book)
        val stream = client.getStream("${Books.baseUrl(book)}book.txt")
        val br = BufferedReader(InputStreamReader(stream, Const.ENCODING), 1000)
        val max = br.readLine().toInt() //count pages
        var s: String? = br.readLine()
        while (s != null && isRun) {
            val link = prefix + s
            val cursor = storage.searchLink(link)
            if (cursor.moveToFirst()) {
                val id = cursor.getInt(0)
                time = cursor.getLong(1)
                s = br.readLine()
                if (time != s.toLong().apply { time = this }) {
                    storage.deleteParagraphs(id)
                    s = br.readLine()
                    while (s != Const.END && s != null) {
                        storage.insertParagraph(id, s)
                        s = br.readLine()
                    }
                    storage.updateTime(link, time)
                }
                cur++
                handler.postPercent(cur.percent(max))
            }
            cursor.close()
            s = br.readLine()
        }
        br.close()
        storage.close()
    }
}