package ru.neosvet.vestnewage.viewmodel

import android.content.ContentValues
import android.content.Context
import androidx.work.Data
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.helper.BookHelper
import ru.neosvet.vestnewage.helper.BrowserHelper
import ru.neosvet.vestnewage.loader.page.PageLoader
import ru.neosvet.vestnewage.loader.page.StyleLoader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.storage.JournalStorage
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.isPoem
import ru.neosvet.vestnewage.viewmodel.basic.BrowserStrings
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.util.*

class BrowserToiler : NeoToiler() {
    companion object {
        private const val FILE = "file://"
        private const val STYLE = "/style/style.css"
        private const val FONT = "/style/myriad.ttf"
        private const val PAGE = "/page.html"
        private const val script = "<a href='javascript:NeoInterface."
        private const val PERIOD_FOR_REFRESH = DateUnit.DAY_IN_SEC * 30
    }

    private lateinit var strings: BrowserStrings
    private val storage = PageStorage()
    var lightTheme: Boolean = true
    private val history = Stack<String>()
    private var isRefresh = false
    var helper: BrowserHelper? = null
    private var link: String
        get() = helper!!.link
        set(value) {
            helper!!.link = value
        }
    private val pageLoader: PageLoader by lazy {
        PageLoader()
    }
    private val styleLoader: StyleLoader by lazy {
        StyleLoader()
    }

    fun init(context: Context) {
        strings = BrowserStrings(
            page = context.getString(R.string.format_page),
            copyright = "<br> " + context.getString(R.string.copyright),
            downloaded = context.getString(R.string.downloaded),
            toPrev = context.getString(R.string.to_prev),
            toNext = context.getString(R.string.to_next)
        )
        helper = BrowserHelper(context)
    }

    fun refresh() {
        isRefresh = true
        load()
    }

    override suspend fun doLoad() {
        storage.close()
        restoreStyle()
        styleLoader.download(isRefresh)
        pageLoader.download(link, true)
        openPage(true)
    }

    override fun onDestroy() {
        storage.close()
    }

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, BrowserHelper.TAG)
        .putString(Const.LINK, link)
        .build()

    fun openLink(url: String, addHistory: Boolean) {
        if (url.isEmpty()) return
        if (!url.contains(Const.HTML) && !url.contains("http:")) {
            Lib.openInApps(url, null)
            return
        }
        if (link != url && !url.contains(PAGE)) {
            if (link.isNotEmpty()) {
                storage.close()
                if (addHistory)
                    history.push(link)
            }
            link = url
        }
        openPage(true)
    }

    fun openPage(newPage: Boolean) {
        scope.launch {
            storage.open(link)
            loadIfNeed = true
            if (storage.name.contains(".")) {
                if (storage.isOldBook) loadIfNeed = false
            }
            if (storage.existsPage(link).not()) {
                if (loadIfNeed) {
                    isRefresh = false
                    reLoad()
                } else
                    postState(NeoState.Ready)
                return@launch
            }
            if (!preparingStyle()) return@launch
            val file = Lib.getFile(PAGE)
            val p = if (newPage || !file.exists())
                generatePage(file)
            else Pair(false, false) //isNeedUpdate, isOtrk
            var s = file.toString()
            if (link.contains("#"))
                s += link.substring(link.indexOf("#"))
            postState(
                NeoState.Page(
                    url = FILE + s,
                    isOtkr = p.second
                )
            )
            if (p.first.not()) { //not isNeedUpdate
                if (isRefresh.not()) addJournal()
                return@launch
            }
            isRefresh = true
            reLoad()
        }
    }

    fun onBackBrowser(): Boolean {
        if (history.isEmpty())
            return false
        openLink(history.pop(), false)
        return true
    }

    private fun generatePage(file: File): Pair<Boolean, Boolean> { //isNeedUpdate, isOtrk
        val bw = BufferedWriter(FileWriter(file))
        storage.open(link)
        var cursor = storage.getPage(link)
        val id: Int
        var isNeedUpdate = false
        var isOtkr = false
        val d: DateUnit
        if (cursor.moveToFirst()) {
            val iId = cursor.getColumnIndex(DataBase.ID)
            id = cursor.getInt(iId)
            val iTitle = cursor.getColumnIndex(Const.TITLE)
            val s = storage.getPageTitle(cursor.getString(iTitle), link)
            val iTime = cursor.getColumnIndex(Const.TIME)
            d = DateUnit.putMills(cursor.getLong(iTime))
            if (storage.isArticle()) //обновлять только статьи
                isNeedUpdate =
                    DateUnit.initNow().timeInSeconds - d.timeInSeconds > PERIOD_FOR_REFRESH
            bw.write("<html><head>\n<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n")
            bw.write("<title>")
            bw.write(s)
            bw.write("</title>\n")
            bw.write("<link rel=\"stylesheet\" type=\"text/css\" href=\"")
            bw.flush()
            bw.write(STYLE.substring(1))
            bw.write("\">\n</head><body>\n<h1 class=\"page-title\">")
            bw.write(s)
            bw.write("</h1>\n")
            bw.flush()
        } else {
            //заголовка нет - значит нет и страницы
            //сюда никогдане попадет, т.к. выше есть проверка existsPage
            cursor.close()
            return Pair(isNeedUpdate, isOtkr)
        }
        cursor.close()
        cursor = storage.getParagraphs(id)
        val poems = link.contains("poems/")
        if (cursor.moveToFirst()) {
            do {
                if (poems) {
                    bw.write("<p class='poem'")
                    bw.write(cursor.getString(0).substring(2))
                } else bw.write(cursor.getString(0))
                bw.write(Const.N)
                bw.flush()
            } while (cursor.moveToNext())
        }
        cursor.close()
        bw.write("<div style=\"margin-top:20px\" class=\"print2\">\n")
        if (storage.isBook()) {
            bw.write(script)
            bw.write("PrevPage();'>" + strings.toPrev + "</a> | ")
            bw.write(script)
            bw.write("NextPage();'>" + strings.toNext + "</a>")
            bw.write(Const.BR)
            bw.flush()
        }
        if (link.contains("print")) { // материалы с сайта Откровений
            isOtkr = true
            bw.write(strings.copyright)
            bw.write(d.year.toString() + Const.BR)
        } else {
            val url = NeoClient.SITE + link
            bw.write(strings.page.format(url, url))
            bw.write(strings.copyright)
            bw.write(d.year.toString() + Const.BR)
            bw.write(strings.downloaded + " " + d.toString())
        }
        bw.write("\n</div></body></html>")
        bw.close()
        return Pair(isNeedUpdate, isOtkr)
    }

    private fun preparingStyle(): Boolean {
        val fLight = Lib.getFile(Const.LIGHT)
        val fDark = Lib.getFile(Const.DARK)
        if (!fLight.exists() && !fDark.exists()) { //download style
            storage.close()
            scope.launch {
                styleLoader.download(true)
                openPage(true)
            }
            return false
        }
        val fStyle = Lib.getFile(STYLE)
        var replace = true
        if (fStyle.exists()) {
            replace = fDark.exists() && !lightTheme || fLight.exists() && lightTheme
            if (replace) {
                if (fDark.exists())
                    fStyle.renameTo(fLight)
                else
                    fStyle.renameTo(fDark)
            }
        }
        if (replace) {
            if (lightTheme) fLight.renameTo(fStyle) else fDark.renameTo(fStyle)
        }
        preparingFont()
        return true
    }

    private fun preparingFont() {
        val font = Lib.getFile(FONT)
        if (font.exists().not()) {
            val inStream = App.context.resources.openRawResource(R.font.myriad)
            val outStream = FileOutputStream(font)
            val buffer = ByteArray(1024)
            var length = inStream.read(buffer)
            while (length > 0) {
                outStream.write(buffer, 0, length)
                length = inStream.read(buffer)
            }
            inStream.close()
            outStream.close()
        }
    }

    private suspend fun addJournal() {
        val row = ContentValues()
        row.put(Const.TIME, System.currentTimeMillis())
        val id = PageStorage.getDatePage(link) + Const.AND + storage.getPageId(link)
        val dbJournal = JournalStorage()
        try {
            if (!dbJournal.update(id, row)) {
                row.put(DataBase.ID, id)
                dbJournal.insert(row)
            }
            dbJournal.checkLimit()
            dbJournal.close()
        } catch (e: Exception) {
            dbJournal.close()
            val file = Lib.getFileDB(DataBase.JOURNAL)
            file.delete()
        }
    }

    private fun restoreStyle() {
        val fStyle = Lib.getFile(STYLE)
        if (fStyle.exists()) {
            val fDark = Lib.getFile(Const.DARK)
            if (fDark.exists())
                fStyle.renameTo(Lib.getFile(Const.LIGHT))
            else
                fStyle.renameTo(fDark)
        }
    }

    private fun getDateFromLink(): DateUnit {
        var s = link.substring(link.lastIndexOf("/") + 1, link.lastIndexOf("."))
        if (s.contains("_")) s = s.substring(0, s.indexOf("_"))
        return DateUnit.parse(s)
    }

    fun nextPage() {
        storage.open(link)
        storage.getNextPage(link)?.let {
            openLink(it, false)
            return
        }
        val today = DateUnit.initToday().my
        val d: DateUnit = getDateFromLink()
        if (d.my == today) {
            setState(NeoState.Success)
            return
        }
        d.changeMonth(1)
        storage.open(d.my)
        val cursor = storage.getList(link.isPoem)
        if (cursor.moveToFirst()) {
            val iLink = cursor.getColumnIndex(Const.LINK)
            openLink(cursor.getString(iLink), false)
        }
    }

    fun prevPage() {
        storage.open(link)
        storage.getPrevPage(link)?.let {
            openLink(it, false)
            return
        }
        val min: String = getMinMY()
        val d = getDateFromLink()
        if (d.my == min) {
            setState(NeoState.Success)
            return
        }
        d.changeMonth(-1)
        storage.open(d.my)
        val cursor = storage.getList(link.isPoem)
        if (cursor.moveToLast()) {
            val iLink = cursor.getColumnIndex(Const.LINK)
            openLink(cursor.getString(iLink), false)
        }
    }

    private fun getMinMY(): String {
        if (link.isPoem) {
            val d = DateUnit.putYearMonth(2016, 2)
            return d.my
        }
        val book = BookHelper()
        val d = if (book.isLoadedOtkr())
            DateUnit.putYearMonth(2004, 8)
        else
            DateUnit.putYearMonth(2016, 1)
        return d.my
    }
}