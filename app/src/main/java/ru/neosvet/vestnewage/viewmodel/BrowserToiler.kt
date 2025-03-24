package ru.neosvet.vestnewage.viewmodel

import android.content.ContentValues
import android.content.Context
import androidx.work.Data
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.helper.BrowserHelper
import ru.neosvet.vestnewage.helper.DateHelper
import ru.neosvet.vestnewage.loader.AdditionLoader
import ru.neosvet.vestnewage.loader.page.PageLoader
import ru.neosvet.vestnewage.loader.page.StyleLoader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.OnlineObserver
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.AdditionStorage
import ru.neosvet.vestnewage.storage.DataBase
import ru.neosvet.vestnewage.storage.JournalStorage
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Files
import ru.neosvet.vestnewage.utils.dateFromLink
import ru.neosvet.vestnewage.utils.hasDate
import ru.neosvet.vestnewage.utils.isDoctrineBook
import ru.neosvet.vestnewage.utils.isPoem
import ru.neosvet.vestnewage.viewmodel.basic.BrowserStrings
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.BrowserState
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.DataOutputStream
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
        private const val PAR_POEM = "<p class='poem'>"
        private const val PERIOD_FOR_REFRESH = DateUnit.DAY_IN_SEC * 30
    }

    private lateinit var strings: BrowserStrings
    private val storage = PageStorage()
    private val history = Stack<String>()
    private var isRefresh = false
    private var isDoctrine = false
    private var isHolyRus = false
    private var isNumPar = false
    private var isPaging = false
    private var withOutPosition = false
    private var isLightTheme = true
    private var link = ""
    private var nextLink = ""
    private var idPage = ""
    private var reactionDay = 0
    private var reactionPosition = 0
    private val pageLoader: PageLoader by lazy {
        PageLoader(NeoClient())
    }
    private val styleLoader: StyleLoader by lazy {
        StyleLoader()
    }

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, BrowserHelper.TAG)
        .putString(Const.LINK, link)
        .build()

    override fun init(context: Context) {
        strings = BrowserStrings(
            copyright = "<br> " + context.getString(R.string.copyright),
            downloaded = context.getString(R.string.downloaded),
            doctrinePages = context.getString(R.string.doctrine_pages),
            holyRusPages = context.getString(R.string.holy_rus_pages),
            doctrineFuture = context.getString(R.string.doctrine_future),
            editionOf = context.getString(R.string.edition_of),
            publicationOf = context.getString(R.string.publication_of),
            toPrev = context.getString(R.string.to_prev),
            toNext = context.getString(R.string.to_next),
            showReaction = context.getString(R.string.show_reaction),
            notFoundReaction = context.getString(R.string.not_found_reaction),
            footReaction = context.getString(R.string.foot_reaction)
        )
    }

    override suspend fun defaultState() {
        loadIfNeed = true
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
        if (!isDoctrine && !isHolyRus && !url.contains(Const.HTML) && !url.contains("http:")) {
            Urls.openInApps(url)
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
            var type = BrowserState.Type.NEW_BOOK
            if (storage.name.contains(".")) {
                if (storage.isOldBook) {
                    type = BrowserState.Type.OLD_BOOK
                    loadIfNeed = link == Urls.PRED_LINK
                }
            }
            when {
                storage.isDoctrine -> {
                    type = BrowserState.Type.DOCTRINE
                    isDoctrine = true
                    isPaging = link.isDoctrineBook
                }

                storage.isHolyRus -> {
                    type = BrowserState.Type.HOLY_RUS
                    isHolyRus = true
                    isPaging = true
                }

                else -> isPaging = storage.isBook
            }
            if (storage.existsPage(link).not()) {
                if (loadIfNeed) {
                    isRefresh = false
                    reLoad()
                } else postState(BasicState.Ready)
                return@launch
            }
            if (!preparingStyle()) return@launch
            val file = Files.file(PAGE)

            val isNeedUpdate = if (newPage || !file.exists())
                generatePage(file)
            else false
            var p = 0f
            if (isNeedUpdate) {
                isRefresh = true
                reLoad()
            } else if (isRefresh.not())
                p = addJournal()
            if (reactionPosition > 0)
                p = -reactionPosition.toFloat()
            else if (withOutPosition) {
                p = 0f
                withOutPosition = false
            }
            postState(
                BrowserState.Primary(
                    url = FILE + file.toString(),
                    link = link,
                    position = p,
                    type = type
                )
            )
        }
    }

    fun onBackBrowser(): Boolean {
        if (history.isEmpty())
            return false
        openLink(history.pop(), false)
        return true
    }

    private suspend fun generatePage(file: File): Boolean { //isNeedUpdate
        val linkDay: Int
        if (link.hasDate) {
            linkDay = link.dateFromLink.timeInDays
            withContext(Dispatchers.IO) {
                val output = App.context.openFileOutput(Files.DATE, Context.MODE_PRIVATE)
                val stream = DataOutputStream(BufferedOutputStream(output))
                stream.writeInt(linkDay)
                stream.close()
            }
            reactionDay = linkDay
        } else {
            linkDay = 0
            val f = Files.slash(Files.DATE)
            if (f.exists()) f.delete()
        }
        return withContext(Dispatchers.IO) {
            val bw = BufferedWriter(FileWriter(file))
            storage.open(link)
            var cursor = storage.getPage(link)
            val id: Int
            var isNeedUpdate = false
            val d: DateUnit
            var par = 1
            var n: Int
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
                //сюда никогда не попадет, т.к. выше есть проверка existsPage
                cursor.close()
                return@withContext isNeedUpdate
            }
            cursor.close()
            cursor = storage.getParagraphs(id)
            nextLink = ""
            var s: String
            val poems = link.isPoem
            if (cursor.moveToFirst()) {
                do {
                    s = cursor.getString(0)
                    if (poems) {
                        if (s.startsWith("<p") && !s.contains("class"))
                            s = PAR_POEM + s.substring(s.indexOf(">") + 1)
                        if (isNumPar && !s.contains("noind")) {
                            n = s.indexOf(">") + 1
                            s = s.substring(0, n) + "$par. " + s.substring(n)
                            par++
                            bw.write(s)
                        } else {
                            if (s.contains("href")) nextLink = s
                            bw.write(s)
                        }
                    } else bw.write(s)
                    bw.write(Const.N)
                    bw.flush()
                } while (cursor.moveToNext())
            }
            cursor.close()
            var reactionContent = ""
            bw.write("<div style='margin-top:20px' class='print2'>\n")
            if (isPaging) {
                bw.write(SCRIPT)
                bw.write("PrevPage();' value='" + strings.toPrev + "'/> | ")
                bw.write(SCRIPT)
                bw.write("NextPage();' value='" + strings.toNext + "'/>")
                bw.write(Const.BR)
                bw.write(Const.BR)
                if (linkDay >= DateHelper.MIN_DAYS_REACTIONS) {
//TODO if showReaction==true && reactionPosition==0 then calc reactionPosition?
                    if (BrowserHelper.showReaction)
                        reactionContent = searchReaction()
                    bw.write("<label><input type='checkbox' onchange='NeoInterface.")
                    bw.write("ChangeReaction(this.checked ? true : false);'")
                    if (BrowserHelper.showReaction) bw.write(" checked>")
                    else bw.write(">")
                    bw.write(strings.showReaction + "</label>")
                    bw.write(Const.BR)
                    if (BrowserHelper.showReaction && reactionContent.isEmpty()) {
                        bw.write(strings.notFoundReaction)
                        bw.write(Const.BR)
                    }
                    bw.write(Const.BR)
                }
                bw.flush()
            }
            when {
                isDoctrine -> {
                    if (link.isDoctrineBook)
                        bw.write(strings.doctrinePages + link.substring(Const.DOCTRINE.length))
                    else bw.write(
                        strings.doctrineFuture +
                                link.replace(Const.DOCTRINE, Urls.DOCTRINE)
                    )
                    bw.write(strings.copyright)
                    bw.write(DateUnit.initToday().year.toString() + Const.BR)
                    if (link.isDoctrineBook) bw.write(strings.editionOf + d.toString())
                    else bw.write(strings.publicationOf + d.toString())
                }

                isHolyRus -> {
                    bw.write(strings.holyRusPages + link.substring(Const.HOLY_RUS.length))
                    bw.write(strings.copyright)
                    bw.write(DateUnit.initToday().year.toString() + Const.BR)
                    bw.write(strings.editionOf + d.toString())
                }

                else -> {
                    if (!storage.isOldBook || Urls.isSiteCom) {
                        val url = Urls.Site + link
                        bw.write(LINK_FORMAT.format(url, url))
                    }
                    bw.write(strings.copyright)
                    bw.write(d.year.toString())
                    if (!storage.isOldBook)
                        bw.write(Const.BR + strings.downloaded + d.toString())
                }
            }
            bw.write("\n</div>")
            if (reactionContent.isNotEmpty()) bw.write(reactionContent)
            bw.write("</body></html>")
            bw.close()
            isNeedUpdate
        }
    }

    private fun preparingStyle(): Boolean {
        val fLight = Files.file(StyleLoader.LIGHT)
        val fDark = Files.file(StyleLoader.DARK)
        if (!fLight.exists() && !fDark.exists()) { //download style
            storage.close()
            scope.launch {
                styleLoader.download(true)
                openPage(true)
            }
            return false
        }
        val fStyle = Files.file(STYLE)
        var replace = true
        if (fStyle.exists()) {
            replace = fDark.exists() && !isLightTheme || fLight.exists() && isLightTheme
            if (replace) {
                if (fDark.exists()) fStyle.renameTo(fLight)
                else fStyle.renameTo(fDark)
            }
        }
        if (replace) {
            if (isLightTheme) fLight.renameTo(fStyle) else fDark.renameTo(fStyle)
        }
        preparingFont()
        return true
    }

    private fun preparingFont() {
        val font = Files.file(FONT)
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

    private fun addJournal(): Float {
        val row = ContentValues()
        row.put(Const.TIME, System.currentTimeMillis())
        idPage = PageStorage.getDatePage(link) + Const.AND + storage.getPageId(link)
        val dbJournal = JournalStorage()
        try {
            val position = dbJournal.getPlace(idPage)
            if (!dbJournal.update(idPage, row)) {
                row.put(DataBase.ID, idPage)
                dbJournal.insert(row)
            }
            dbJournal.checkLimit()
            dbJournal.close()
            return position
        } catch (e: Exception) {
            dbJournal.close()
            val file = Files.dateBase(DataBase.JOURNAL)
            file.delete()
        }
        return 0f
    }

    private fun restoreStyle() {
        val fStyle = Files.file(STYLE)
        if (fStyle.exists()) {
            val fDark = Files.file(StyleLoader.DARK)
            if (fDark.exists())
                fStyle.renameTo(Files.file(StyleLoader.LIGHT))
            else fStyle.renameTo(fDark)
        }
    }

    fun nextPage() {
        if (!isPaging) return
        withOutPosition = true
        storage.open(link)
        if (nextLink.isNotEmpty()) {
            var a = nextLink.indexOf("href") + 6
            val b = nextLink.indexOf(">", a) + 1
            val s = nextLink.substring(a, b - 2)
            if (!storage.existsPage(s)) {
                a = nextLink.indexOf("<", b)
                val t = nextLink.substring(b, a)
                storage.putTitle(t, s, System.currentTimeMillis())
            }
        }
        storage.getNextPage(link)?.let {
            if (!isDoctrine || it.isDoctrineBook) {
                openLink(it, false)
                return
            }
        }
        if (isDoctrine || isHolyRus) {
            setState(BasicState.Success)
            return
        }
        val d = link.dateFromLink
        if (d.my == DateUnit.initToday().my) {
            setState(BasicState.Success)
            return
        }
        d.changeMonth(1)
        storage.open(d.my)
        val cursor = storage.getList(link.isPoem)
        if (cursor.moveToFirst()) {
            val iLink = cursor.getColumnIndex(Const.LINK)
            openLink(cursor.getString(iLink), false)
        } else setState(BasicState.NotLoaded)
        cursor.close()
    }

    fun prevPage() {
        if (!isPaging) return
        withOutPosition = true
        storage.open(link)
        storage.getPrevPage(link)?.let {
            if (!isDoctrine || it.isDoctrineBook) {
                openLink(it, false)
                return
            }
        }
        if (isDoctrine || isHolyRus) {
            setState(BasicState.Success)
            return
        }
        val d = link.dateFromLink
        if (d.my == getMinMY()) {
            setState(BasicState.Success)
            return
        }
        d.changeMonth(-1)
        storage.open(d.my)
        val cursor = storage.getList(link.isPoem)
        if (cursor.moveToLast()) {
            val iLink = cursor.getColumnIndex(Const.LINK)
            openLink(cursor.getString(iLink), false)
        } else setState(BasicState.NotLoaded)
        cursor.close()
    }

    private fun getMinMY(): String {
        if (link.isPoem) {
            val d = DateUnit.putDays(DateHelper.MIN_DAYS_POEMS)
            return d.my
        }
        val d = if (DateHelper.isLoadedOtkr())
            DateUnit.putDays(DateHelper.MIN_DAYS_OLD_BOOK)
        else DateUnit.putDays(DateHelper.MIN_DAYS_NEW_BOOK)
        return d.my
    }

    fun setArgument(link: String, isLightTheme: Boolean, isNumPar: Boolean) {
        this.link = link
        this.isLightTheme = isLightTheme
        this.isNumPar = isNumPar
    }

    fun savePosition(positionOnPage: Float) {
        val dbJournal = JournalStorage()
        val row = ContentValues()
        row.put(Const.PLACE, positionOnPage)
        dbJournal.update(idPage, row)
        dbJournal.close()
    }

    fun switchReaction(heightPage: Int) {
        scope.launch {
            if (heightPage == -1) {
                BrowserHelper.showReaction = false
            } else {
                BrowserHelper.showReaction = true
                reactionPosition = heightPage
            }
            withOutPosition = true
            openPage(true)
        }
    }

    private suspend fun searchReaction(): String {
        val date = DateUnit.putDays(reactionDay).toShortDateString()
        val storage = AdditionStorage()
        storage.open()
        var s = startSearchReaction(storage, date)
        if (s.isEmpty() && OnlineObserver.isOnline.value) {
            postState(BasicState.Loading)
            val loader = AdditionLoader(NeoClient())
            loader.load(storage, 0)
            s = startSearchReaction(storage, date)
            if (s.isEmpty()) {
                loader.loadAll(null)
                s = startSearchReaction(storage, date)
            }
        }
        storage.close()
        return s
    }

    private fun startSearchReaction(storage: AdditionStorage, date: String): String {
        val cursor = storage.search(date)
        val sb = StringBuilder()
        if (cursor.moveToFirst()) {
            val iLink = cursor.getColumnIndex(Const.LINK)
            val iTitle = cursor.getColumnIndex(Const.TITLE)
            val iDes = cursor.getColumnIndex(Const.DESCRIPTION)
            sb.append("<h3>")
            sb.append(cursor.getString(iTitle))
            sb.append("</h3>")
            val s = cursor.getString(iDes)
            if (s.contains("<")) sb.append(s)
            else {
                var p = 0
                s.lines().forEach {
                    if (p == 0) p++
                    else if (it.length < 2) {
                        sb.append("</p>")
                        p = 1
                    } else {
                        if (p == 1) {
                            sb.append(PAR_POEM)
                            p = 2
                        } else sb.append("<br>")
                        sb.append(it)
                    }
                }
                sb.append("</p>")
                sb.append("<div style='margin-top:20px' class='print2'>\n")
                sb.append(strings.footReaction)
                sb.append(Const.BR)
                sb.append("<a href='")
                val link = Urls.TelegramUrl + cursor.getString(iLink)
                sb.append(link)
                sb.append("'>")
                sb.append(link)
                sb.append("</a>")
                sb.append("</div>")
            }
        }
        cursor.close()
        return sb.toString()
    }

}