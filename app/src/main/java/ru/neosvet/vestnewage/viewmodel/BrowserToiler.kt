package ru.neosvet.vestnewage.viewmodel

import android.content.ContentValues
import android.content.Context
import androidx.work.Data
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.helper.BrowserHelper
import ru.neosvet.vestnewage.helper.DateHelper
import ru.neosvet.vestnewage.loader.page.PageLoader
import ru.neosvet.vestnewage.loader.page.StyleLoader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.JournalStorage
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.date
import ru.neosvet.vestnewage.utils.isPoem
import ru.neosvet.vestnewage.viewmodel.basic.BrowserStrings
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.BrowserState
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.util.Stack

class BrowserToiler : NeoToiler() {
    companion object {
        private const val FILE = "file://"
        private const val LINK_FORMAT = "<a href='%s'>%s</a>"
        private const val STYLE = "/style/style.css"
        private const val FONT = "/style/myriad.ttf"
        private const val PAGE = "/page.html"
        private const val SCRIPT = "<input type='button' onclick='NeoInterface."
        private const val PERIOD_FOR_REFRESH = DateUnit.DAY_IN_SEC * 30
    }

    private lateinit var strings: BrowserStrings
    private val storage = PageStorage()
    private val history = Stack<String>()
    private var isRefresh = false
    private lateinit var helper: BrowserHelper
    private var link: String
        get() = helper.link
        set(value) {
            helper.link = value
        }
    private val pageLoader: PageLoader by lazy {
        PageLoader(NeoClient(NeoClient.Type.SECTION))
    }
    private val styleLoader: StyleLoader by lazy {
        StyleLoader()
    }

    private val dateFromLink: DateUnit
        get() = DateUnit.parse(link.date)

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, BrowserHelper.TAG)
        .putString(Const.LINK, link)
        .build()

    override fun init(context: Context) {
        helper = BrowserHelper(context)
        strings = BrowserStrings(
            copyright = "<br> " + context.getString(R.string.copyright),
            downloaded = context.getString(R.string.downloaded),
            doctrine_pages = context.getString(R.string.doctrine_pages),
            edition_of = context.getString(R.string.edition_of),
            toPrev = context.getString(R.string.to_prev),
            toNext = context.getString(R.string.to_next)
        )
    }

    override suspend fun defaultState() {
        postState(BrowserState.Primary(helper))
        if (link.isNotEmpty()) openPage(true)
    }

    override suspend fun doLoad() {
        storage.close()
        restoreStyle()
        styleLoader.download(isRefresh)
        if (isRun.not()) return
        currentLoader = pageLoader
        pageLoader.download(link, true)
        openPage(true)
    }

    fun refresh() {
        isRefresh = true
        load()
    }

    override fun onDestroy() {
        storage.close()
    }

    fun openLink(url: String, addHistory: Boolean) {
        if (url.isEmpty()) return
        if (!url.contains(Const.HTML) && !url.contains("http:")
            && !url.contains(Const.DOCTRINE)
        ) {
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
        loadIfNeed = true
        openPage(true)
    }

    fun openPage(newPage: Boolean) {
        cancel()
        scope.launch {
            storage.open(link)
            if (storage.name.contains(".")) {
                if (storage.isOldBook) loadIfNeed = false
            }
            if (storage.existsPage(link).not()) {
                if (loadIfNeed) {
                    isRefresh = false
                    reLoad()
                } else
                    postState(BasicState.Ready)
                return@launch
            }
            if (!preparingStyle()) return@launch
            val file = Lib.getFile(PAGE)
            val p = if (newPage || !file.exists())
                generatePage(file)
            else Pair(false, false) //isNeedUpdate, isOtkr
            postState(
                BrowserState.Page(
                    url = FILE + file.toString(),
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

    private fun generatePage(file: File): Pair<Boolean, Boolean> { //isNeedUpdate, isOtkr
        val bw = BufferedWriter(FileWriter(file))
        storage.open(link)
        var cursor = storage.getPage(link)
        val id: Int
        var isNeedUpdate = false
        var isOtkr = false
        val d: DateUnit
        var n = 1
        if (cursor.moveToFirst()) {
            val iId = cursor.getColumnIndex(DataBase.ID)
            id = cursor.getInt(iId)
            val iTitle = cursor.getColumnIndex(Const.TITLE)
            val s = storage.getPageTitle(cursor.getString(iTitle), link)
            val iTime = cursor.getColumnIndex(Const.TIME)
            d = DateUnit.putMills(cursor.getLong(iTime))
            if (storage.isArticle) //обновлять только статьи
                isNeedUpdate =
                    DateUnit.initNow().timeInSeconds - d.timeInSeconds > PERIOD_FOR_REFRESH
            bw.write("<html><head>\n<meta http-equiv='Content-Type' content='text/html; charset=UTF-8'>\n")
            bw.write("<title>")
            bw.write(s)
            bw.write("</title>\n")
            bw.write("<link rel='stylesheet' type='text/css' href='")
            bw.flush()
            bw.write(STYLE.substring(1))
            bw.write("'>\n</head><body>\n<h1 class='page-title' id='title'>")
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
        var s: String
        val poems = link.isPoem
        if (cursor.moveToFirst()) {
            do {
                s = cursor.getString(0)
                if (poems) {
                    if (helper.isNumPar && !s.contains("noind")) {
                        bw.write("<p class='poem'>")
                        bw.write("$n. ")
                        n++
                        bw.write(s.substring(3))
                    } else {
                        bw.write("<p class='poem'")
                        bw.write(s.substring(2))
                    }
                } else bw.write(s)
                bw.write(Const.N)
                bw.flush()
            } while (cursor.moveToNext())
        }
        cursor.close()
        bw.write("<div style='margin-top:20px' class='print2'>\n")
        if (storage.isBook || helper.isDoctrine) {
            bw.write(SCRIPT)
            bw.write("PrevPage();' value='" + strings.toPrev + "'/> | ")
            bw.write(SCRIPT)
            bw.write("NextPage();' value='" + strings.toNext + "'/>")
            bw.write(Const.BR)
            bw.write(Const.BR)
            bw.flush()
        }
        if (helper.isDoctrine) {
            bw.write(strings.doctrine_pages + link.substring(Const.DOCTRINE.length))
            bw.write(strings.copyright)
            bw.write(DateUnit.initToday().year.toString() + Const.BR)
            bw.write(strings.edition_of + d.toString())
        } else if (link.contains("print")) { // материалы с сайта Откровений
            isOtkr = true
            bw.write(strings.copyright)
            bw.write(d.year.toString() + Const.BR)
        } else {
            val url = Urls.Site + link
            bw.write(LINK_FORMAT.format(url, url))
            bw.write(strings.copyright)
            bw.write(d.year.toString() + Const.BR)
            bw.write(strings.downloaded + d.toString())
        }
        bw.write("\n</div></body></html>")
        bw.close()
        return Pair(isNeedUpdate, isOtkr)
    }

    private fun preparingStyle(): Boolean {
        val fLight = Lib.getFile(StyleLoader.LIGHT)
        val fDark = Lib.getFile(StyleLoader.DARK)
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
        val light = helper.isLightTheme
        if (fStyle.exists()) {
            replace = fDark.exists() && !light || fLight.exists() && light
            if (replace) {
                if (fDark.exists())
                    fStyle.renameTo(fLight)
                else
                    fStyle.renameTo(fDark)
            }
        }
        if (replace) {
            if (light) fLight.renameTo(fStyle) else fDark.renameTo(fStyle)
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
            val fDark = Lib.getFile(StyleLoader.DARK)
            if (fDark.exists())
                fStyle.renameTo(Lib.getFile(StyleLoader.LIGHT))
            else
                fStyle.renameTo(fDark)
        }
    }

    fun nextPage() {
        storage.open(link)
        storage.getNextPage(link)?.let {
            openLink(it, false)
            return
        }
        if (helper.isDoctrine) {
            setState(BasicState.Success)
            return
        }
        val today = DateUnit.initToday().my
        val d = dateFromLink
        if (d.my == today) {
            setState(BasicState.Success)
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
        if (helper.isDoctrine) {
            setState(BasicState.Success)
            return
        }
        val min: String = getMinMY()
        val d = dateFromLink
        if (d.my == min) {
            setState(BasicState.Success)
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
            val d = DateUnit.putDays(DateHelper.MIN_DAYS_POEMS)
            return d.my
        }
        val d = if (DateHelper.isLoadedOtkr())
            DateUnit.putDays(DateHelper.MIN_DAYS_OLD_BOOK)
        else
            DateUnit.putDays(DateHelper.MIN_DAYS_NEW_BOOK)
        return d.my
    }

    fun setArgument(link: String, search: String?) {
        this.link = link
        search?.let { helper.setSearchString(it) }
    }
}