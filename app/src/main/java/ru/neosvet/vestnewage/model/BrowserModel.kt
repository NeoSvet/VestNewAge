package ru.neosvet.vestnewage.model

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.*
import ru.neosvet.utils.Const
import ru.neosvet.utils.DataBase
import ru.neosvet.utils.Lib
import ru.neosvet.utils.NeoClient
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.helpers.BookHelper
import ru.neosvet.vestnewage.helpers.BrowserHelper
import ru.neosvet.vestnewage.helpers.DateHelper
import ru.neosvet.vestnewage.loader.PageLoader
import ru.neosvet.vestnewage.loader.StyleLoader
import ru.neosvet.vestnewage.model.state.BrowserState
import ru.neosvet.vestnewage.storage.JournalStorage
import ru.neosvet.vestnewage.storage.PageStorage
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.*

class BrowserModel : ViewModel() {
    companion object {
        private const val FILE = "file://"
        private const val STYLE = "/style/style.css"
        private const val PAGE = "/page.html"
        private const val script = "<a href='javascript:NeoInterface."
    }

    private val mstate = MutableLiveData<BrowserState>()
    val state: LiveData<BrowserState>
        get() = mstate
    private lateinit var strings: BrowserStrings
    private val storage = PageStorage()
    var lightTheme: Boolean = true
    private val history = Stack<String>()
    var helper: BrowserHelper? = null
    private var link: String
        get() = helper!!.link
        set(value) {
            helper!!.link = value
        }
    private val pageLoader: PageLoader by lazy {
        PageLoader(false)
    }
    private val styleLoader: StyleLoader by lazy {
        StyleLoader()
    }
    private val scope = CoroutineScope(Dispatchers.IO
            + CoroutineExceptionHandler { _, throwable ->
        mstate.postValue(BrowserState.Error(throwable))
    })

    fun init(context: Context) {
        strings = BrowserStrings(
            page = context.getString(R.string.page),
            copyright = "<br> " + context.getString(R.string.copyright),
            downloaded = context.getString(R.string.downloaded),
            toPrev = context.getString(R.string.to_prev),
            toNext = context.getString(R.string.to_next)
        )
        helper = BrowserHelper(context)
    }

    override fun onCleared() {
        scope.cancel()
        storage.close()
        super.onCleared()
    }

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
        storage.open(link)
        if (storage.existsPage(link).not()) {
            downloadPage(false)
            return
        }
        if (!readyStyle()) return
        try {
            val file = Lib.getFile(PAGE)
            val p = if (newPage || !file.exists())
                generatePage(file)
            else Pair(0L, false)
            var s = file.toString()
            if (link.contains("#"))
                s += link.substring(link.indexOf("#"))
            mstate.postValue(
                BrowserState.Page(
                    url = FILE + s,
                    timeInSeconds = p.first,
                    isOtkr = p.second
                )
            )
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun downloadPage(update: Boolean) {
        mstate.postValue(BrowserState.Loading)
        scope.launch {
            if (update)
                storage.deleteParagraphs(storage.getPageId(link))
            storage.close()
            styleLoader.download(false)
            pageLoader.download(link, true)
            openPage(true)
        }
    }

    fun onBackBrowser(): Boolean {
        if (history.isEmpty())
            return false
        openLink(history.pop(), false)
        return true
    }

    private fun generatePage(file: File): Pair<Long, Boolean> {
        val bw = BufferedWriter(FileWriter(file))
        storage.open(link)
        var cursor = storage.getPage(link)
        val id: Int
        var time = 0L
        var isOtkr = false
        val d: DateHelper
        if (cursor.moveToFirst()) {
            val iId = cursor.getColumnIndex(DataBase.ID)
            id = cursor.getInt(iId)
            val iTitle = cursor.getColumnIndex(Const.TITLE)
            val s = storage.getPageTitle(cursor.getString(iTitle), link)
            val iTime = cursor.getColumnIndex(Const.TIME)
            d = DateHelper.putMills(cursor.getLong(iTime))
            if (storage.isArticle()) //раз в неделю предлагать обновить статьи
                time = d.timeInSeconds
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
            return Pair(time, isOtkr)
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
            bw.write(strings.page + " " + NeoClient.SITE + link)
            bw.write(strings.copyright)
            bw.write(d.year.toString() + Const.BR)
            bw.write(strings.downloaded + " " + d.toString())
        }
        bw.write("\n</div></body></html>")
        bw.close()
        return Pair(time, isOtkr)
    }

    private fun readyStyle(): Boolean {
        val fLight = Lib.getFileL(Const.LIGHT)
        val fDark = Lib.getFileL(Const.DARK)
        if (!fLight.exists() && !fDark.exists()) { //download style
            storage.close()
            scope.launch {
                styleLoader.download(true)
                openPage(true)
            }
            return false
        }
        val fStyle = Lib.getFileL(STYLE)
        var replace = true
        if (fStyle.exists()) {
            replace = fDark.exists() && !lightTheme || fLight.exists() && lightTheme
            if (replace) {
                if (fDark.exists()) fStyle.renameTo(fLight) else fStyle.renameTo(fDark)
            }
        }
        if (replace) {
            if (lightTheme) fLight.renameTo(fStyle) else fDark.renameTo(fStyle)
        }
        return true
    }

    fun addJournal() {
        scope.launch {
            val row = ContentValues()
            row.put(Const.TIME, System.currentTimeMillis())
            val id = PageStorage.getDatePage(link) + Const.AND + storage.getPageId(link)
            val dbJournal = JournalStorage()
            try {
                if (!dbJournal.update(id, row)) {
                    row.put(DataBase.ID, id)
                    dbJournal.insert(row)
                }
                val cursor = dbJournal.getIds()
                var i = cursor.count
                cursor.moveToFirst()
                while (i > 100) {
                    dbJournal.delete(cursor.getString(0))
                    cursor.moveToNext()
                    i--
                }
                cursor.close()
                dbJournal.close()
            } catch (e: Exception) {
                dbJournal.close()
                val file = Lib.getFileDB(DataBase.JOURNAL)
                file.delete()
            }
        }
    }

    fun restoreStyle() {
        val fStyle = Lib.getFileL(STYLE)
        if (fStyle.exists()) {
            val fDark = Lib.getFileL(Const.DARK)
            if (fDark.exists())
                fStyle.renameTo(Lib.getFileL(Const.LIGHT))
            else
                fStyle.renameTo(fDark)
        }
    }

    private fun getDateFromLink(): DateHelper {
        var s = link.substring(link.lastIndexOf("/") + 1, link.lastIndexOf("."))
        if (s.contains("_")) s = s.substring(0, s.indexOf("_"))
        return DateHelper.parse(s)
    }

    fun nextPage() {
        storage.open(link)
        storage.getNextPage(link)?.let {
            openLink(it, false)
            return
        }
        val today = DateHelper.initToday().my
        val d: DateHelper = getDateFromLink()
        if (d.my == today) {
            mstate.postValue(BrowserState.EndList)
            return
        }
        d.changeMonth(1)
        storage.open(d.my)
        val cursor: Cursor = storage.getList(link.contains(Const.POEMS))
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
            mstate.postValue(BrowserState.EndList)
            return
        }
        d.changeMonth(-1)
        storage.open(d.my)
        val cursor: Cursor = storage.getList(link.contains(Const.POEMS))
        if (cursor.moveToLast()) {
            val iLink = cursor.getColumnIndex(Const.LINK)
            openLink(cursor.getString(iLink), false)
        }
    }

    private fun getMinMY(): String {
        if (link.contains(Const.POEMS)) {
            val d = DateHelper.putYearMonth(2016, 2)
            return d.my
        }
        val book = BookHelper()
        val d = if (book.isLoadedOtkr())
            DateHelper.putYearMonth(2004, 8)
        else
            DateHelper.putYearMonth(2016, 1)
        return d.my
    }

    fun sharePage(context: Context, title: String) {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "text/plain"
        var s: String = title
        if (s.length > 9)
            s = s.substring(9) + " (" +
                    context.getString(R.string.from) +
                    " " + s.substring(0, 8) + ")"
        shareIntent.putExtra(Intent.EXTRA_TEXT, s + Const.N + NeoClient.SITE + link)
        val intent = Intent.createChooser(shareIntent, context.getString(R.string.share))
        context.startActivity(intent)
    }
}