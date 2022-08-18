package ru.neosvet.vestnewage.loader

import android.content.ContentValues
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.loader.basic.LoadHandler
import ru.neosvet.vestnewage.loader.basic.LoadHandlerLite
import ru.neosvet.vestnewage.loader.basic.Loader
import ru.neosvet.vestnewage.loader.page.PageLoader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.percent
import ru.neosvet.vestnewage.viewmodel.SiteToiler
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class MasterLoader : Loader {
    private val handler: LoadHandler?
    private val handlerLite: LoadHandlerLite?

    constructor(handler: LoadHandler) {
        handlerLite = null
        this.handler = handler
    }

    constructor(handler: LoadHandlerLite) {
        this.handler = null
        handlerLite = handler
    }

    companion object {
        private const val UCOZ = "http://neosvet.ucoz.ru/databases_vna"
    }

    private var loader = PageLoader()
    private var loaderBook: BookLoader? = null
    private var isRun = false
    private var lastYear = 0
    private var lastFolder = -1
    private var msg = ""
    private val listBase: MutableList<Pair<Int, List<String>>> by lazy {
        mutableListOf() //Int - number folder, List - dates (MY) in folder
    }

    override fun cancel() {
        loaderBook?.cancel()
        isRun = false
    }

    fun loadSummary() {
        isRun = true
        loader = PageLoader()
        msg = App.context.getString(R.string.summary)
        handler?.postMessage(msg)
        loadList(SummaryLoader().getLinkList())
    }

    fun loadSite() {
        isRun = true
        loader = PageLoader()
        msg = App.context.getString(R.string.news)
        handler?.postMessage(msg)
        val loader = SiteLoader(Lib.getFile(SiteToiler.MAIN).toString())
        loadList(loader.getLinkList())
    }

    fun loadDoctrine() {
        isRun = true
        msg = App.context.getString(R.string.doctrine)
        handler?.postMessage(msg)
        getBookLoader().let {
            it.loadDoctrineList()
            it.loadDoctrinePages()
        }
    }

    private fun getBookLoader(): BookLoader {
        if (loaderBook == null)
            loaderBook = BookLoader()
        return loaderBook!!
    }

    fun loadMonth(month: Int, year: Int) {
        isRun = true
        val d = DateUnit.putYearMonth(year, month)
        msg = d.monthString + " " + d.year
        handler?.postMessage(msg)
        val url = findUrl(d)
        if (url != null) {
            val f = Lib.getFileDB(d.my)
            if (!f.exists() || f.length() == DataBase.EMPTY_BASE_SIZE)
                loadUcozBase(url + d.my)
        } else
            loadFromSite(d)
    }

    private fun loadFromSite(d: DateUnit) {
        if (lastYear != d.year) {
            getBookLoader().loadYearList(d.year)
            lastYear = d.year
        }
        val storage = PageStorage()
        storage.open(d.my)
        val list = storage.getLinksList()
        storage.close()
        loadList(list)
    }

    private fun findUrl(d: DateUnit): String? {
        val my = d.my
        listBase.forEach {
            if (it.second.contains(my)) {
                return if (it.first < 2) "$UCOZ/"
                else "$UCOZ${it.first}/"
            }
        }
        val folder: Int
        val url = UCOZ + when (d.year) {
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
        val list = loadUcozList(url)
        listBase.add(Pair(folder, list))
        if (list.contains(my)) {
            return if (folder < 2) "$UCOZ/"
            else "$UCOZ${folder}/"
        }
        return null
    }

    private fun loadUcozList(url: String): List<String> {
        var name: String
        var f: File
        var l: Long
        //list format:
        //01.05 delete [time] - при необходимости список обновить
        //02.05 [length] - проверка целостности
        val br = BufferedReader(InputStreamReader(NeoClient.getStream(url)), 1000)
        val list = mutableListOf<String>()
        var s: String? = br.readLine()
        var isDelete: Boolean
        while (s != null) {
            if (isRun.not()) {
                br.close()
                return list
            }
            name = s.substring(0, s.indexOf(" "))
            isDelete = s.contains("delete")
            if (isDelete.not())
                list.add(name)
            f = Lib.getFileDB(name)
            if (f.exists()) {
                l = s.substring(s.lastIndexOf(" ") + 1).toLong()
                if (isDelete) {
                    if (f.lastModified() < l)
                        f.delete()
                } else if (l != f.length())
                    f.delete()
            }
            s = br.readLine()
        }
        br.close()
        return list
    }

    private fun loadUcozBase(url: String) {
        val storage = PageStorage()
        var isTitle: Boolean
        val ids = HashMap<String, Int>()
        var id: Int
        var v: String
        val time = System.currentTimeMillis()
        storage.open(url.substring(url.lastIndexOf("/") + 1))
        isTitle = true
        val br = BufferedReader(
            InputStreamReader(NeoClient.getStream(url), Const.ENCODING),
            1000
        )
        var n = 2
        var s: String? = br.readLine()
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

    private fun loadList(list: List<String>) {
        var p = 0
        val max = list.size
        list.forEach {
            loader.download(it, false)
            p++
            if (handler != null)
                handler.postMessage("$msg (${p.percent(max)}%)")
            else
                handlerLite?.postPercent(p.percent(max))
            if (isRun.not()) return@forEach
        }
        loader.finish()
    }
}