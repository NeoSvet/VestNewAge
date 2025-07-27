package ru.neosvet.vestnewage.loader

import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BookTab
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.helper.SiteHelper
import ru.neosvet.vestnewage.helper.SummaryHelper
import ru.neosvet.vestnewage.loader.basic.LoadHandler
import ru.neosvet.vestnewage.loader.basic.LoadHandlerLite
import ru.neosvet.vestnewage.loader.basic.Loader
import ru.neosvet.vestnewage.loader.page.PageLoader
import ru.neosvet.vestnewage.loader.page.StyleLoader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.DataBase
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Files
import ru.neosvet.vestnewage.utils.percent
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class MasterLoader : Loader, LoadHandlerLite {
    private val handler: LoadHandler?
    private val handlerLite: LoadHandlerLite?
    private val clientBase: NeoClient
    private val client: NeoClient
    private val loader: PageLoader

    constructor(handler: LoadHandler) {
        handlerLite = null
        this.handler = handler
        client = NeoClient()
        clientBase = NeoClient(this)
        loader = PageLoader(client)
    }

    constructor(handler: LoadHandlerLite) {
        this.handler = null
        handlerLite = handler
        client = NeoClient(handler)
        clientBase = NeoClient(this)
        loader = PageLoader(client)
    }

    private var loaderBook: BookLoader? = null
    private var isRun = false
    private var lastYear = 0
    private var lastFolder = -1
    private var msg = ""
    private val listBase: MutableList<Pair<Int, List<String>>> by lazy {
        mutableListOf() //Int - number folder, List - dates (MY) in folder
    }

    override fun load() {
        loadStyle()
        loadSummary()
        loadSite()
        loadBook(BookTab.DOCTRINE)
        loadBook(BookTab.HOLY_RUS)
        loadBook(BookTab.WORLD_AFTER_WAR)
        val date = DateUnit.initToday()
        loadMonth(date.month, date.year)
    }

    override fun cancel() {
        loader.cancel()
        loaderBook?.cancel()
        isRun = false
    }

    fun loadStyle() {
        isRun = true
        val style = StyleLoader()
        style.download(false)
        isRun = false
    }

    fun loadSummary() {
        isRun = true
        msg = App.context.getString(R.string.summary)
        handler?.postMessage(msg)
        loadPages(SummaryHelper().getLinkList())
        isRun = false
    }

    fun loadSite() {
        isRun = true
        msg = App.context.getString(R.string.news)
        handler?.postMessage(msg)
        val helper = SiteHelper()
        loadPages(helper.getLinkList())
        isRun = false
    }

    fun loadBook(book: BookTab) {
        //DOCTRINE(2), HOLY_RUS(3), WORLD_AFTER_WAR(4)
        isRun = true
        msg = App.context.getString(
            when (book) {
                BookTab.HOLY_RUS -> R.string.holy_rus
                BookTab.WORLD_AFTER_WAR -> R.string.world_after_war
                else -> R.string.doctrine_creator
            }
        )
        handler?.postMessage(msg)
        getBookLoader().let {
            it.loadBookList(book)
            it.loadBook(book, null)
        }
        isRun = false
    }

    private fun getBookLoader(): BookLoader {
        if (loaderBook == null)
            loaderBook = BookLoader(client)
        return loaderBook!!
    }

    fun loadMonth(month: Int, year: Int) {
        isRun = true
        val d = DateUnit.putYearMonth(year, month)
        msg = d.monthString + " " + d.year
        handler?.postMessage(msg)
        val url = findUrl(d)
        if (url != null) {
            val f = Files.dateBase(d.my)
            if (!f.exists() || f.length() <= DataBase.EMPTY_BASE_SIZE)
                loadBase(url + d.my)
        } else
            loadFromSite(d)
        isRun = false
    }

    private fun loadFromSite(d: DateUnit) {
        if (DataBase.isBusy(d.my)) return
        if (lastYear != d.year) {
            getBookLoader().loadYearList(d.year)
            lastYear = d.year
        }
        val storage = PageStorage()
        storage.open(d.my)
        val list = storage.getLinksList()
        storage.close()
        loadPages(list)
    }

    private fun findUrl(d: DateUnit): String? {
        val my = d.my
        val host = Urls.Databases
        listBase.forEach {
            if (it.second.contains(my)) {
                return if (it.first < 2) "$host/"
                else "$host${it.first}/"
            }
        }
        val folder: Int
        val url = host + when (d.year) {
            in 2004..2015 -> {
                folder = 0
                "/list.txt"
            }

            in 2016..2020 -> {
                folder = 1
                "/list_new.txt"
            }

            else -> {
                folder = 2
                "2/list.txt"
            }
        }
        if (folder == lastFolder)
            return null
        lastFolder = folder
        val list = loadList(url)
        listBase.add(Pair(folder, list))
        if (list.contains(my)) {
            return if (folder < 2) "$host/"
            else "$host${folder}/"
        }
        return null
    }

    private fun loadList(url: String): List<String> {
        var name: String
        var f: File
        var l: Long
        //list format:
        //01.05 delete [time] - при необходимости список обновить
        //02.05 [length] - проверка целостности
        val br = BufferedReader(InputStreamReader(client.getStream(url)), 1000)
        val list = mutableListOf<String>()
        var isDelete: Boolean
        br.forEachLine {
            name = it.substring(0, it.indexOf(" "))
            isDelete = it.contains("delete")
            if (isDelete.not())
                list.add(name)
            f = Files.dateBase(name)
            if (f.exists()) {
                l = it.substring(it.lastIndexOf(" ") + 1).toLong()
                if (isDelete) {
                    if (f.lastModified() < l)
                        f.delete()
                } else if (l != f.length())
                    f.delete()
            }
        }
        br.close()
        return list
    }

    private fun loadBase(url: String) {
        val name = url.substring(url.lastIndexOf("/") + 1)
        if (DataBase.isBusy(name)) return
        val stream = if (Urls.isSiteCom) clientBase.getStream("$url.txt")
        else clientBase.getStream(url)
        val br = BufferedReader(InputStreamReader(stream, Const.ENCODING), 1000)
        val storage = PageStorage()
        storage.open(name, true)
        val time = System.currentTimeMillis()
        storage.updateTime()
        var isTitle = true
        val ids = HashMap<String, Int>()
        var id: Int
        var title: String
        var n = 2
        var s: String? = br.readLine()
        while (s != null && isRun) {
            if (s == Const.AND) {
                isTitle = false
                s = br.readLine()
            }
            title = br.readLine()
            s?.let {
                if (isTitle) {
                    id = storage.putTitle(title, it, time)
                    ids[n.toString()] = id
                    n++
                } else ids[it]?.let { id ->
                    storage.insertParagraph(id, title)
                }
            }
            s = br.readLine()
        }
        br.close()
        storage.close()
    }

    private fun loadPages(list: List<String>) {
        var p = 0
        val max = list.size
        for (it in list) {
            loader.download(it, false)
            p++
            if (handler != null)
                handler.postMessage("$msg (${p.percent(max)}%)")
            else handlerLite?.postPercent(p.percent(max))
            if (isRun.not()) break
        }
        loader.finish()
    }

    override fun postPercent(value: Int) {
        if (!isRun) return
        if (handler != null)
            handler.postMessage("$msg ($value%)")
        else
            handlerLite?.postPercent(value)
    }
}