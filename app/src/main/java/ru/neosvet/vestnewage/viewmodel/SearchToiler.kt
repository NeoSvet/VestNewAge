package ru.neosvet.vestnewage.viewmodel

import android.content.Context
import android.net.Uri
import androidx.core.text.isDigitsOnly
import androidx.paging.PagingData
import androidx.work.Data
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.helper.SearchHelper
import ru.neosvet.vestnewage.loader.CalendarLoader
import ru.neosvet.vestnewage.loader.MasterLoader
import ru.neosvet.vestnewage.loader.basic.LoadHandlerLite
import ru.neosvet.vestnewage.loader.page.PageLoader
import ru.neosvet.vestnewage.network.NetConst
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.storage.SearchStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.SearchEngine
import ru.neosvet.vestnewage.view.list.paging.NeoPaging
import ru.neosvet.vestnewage.view.list.paging.SearchFactory
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.basic.SearchStrings
import java.io.BufferedWriter
import java.io.OutputStreamWriter

class SearchToiler : NeoToiler(), NeoPaging.Parent, SearchEngine.Parent, LoadHandlerLite {
    private val paging = NeoPaging(this)
    override val factory: SearchFactory by lazy {
        SearchFactory(paging)
    }
    val page: Int
        get() = factory.page
    val isLoading: Boolean
        get() = paging.isPaging
    private var isInit = false
    var isExport = false
        private set
    override lateinit var strings: SearchStrings
        private set
    private var labelMode = ""
    override lateinit var helper: SearchHelper
        private set
    var shownResult = false
        private set
    private var loadDate: String? = null
    private var loadLink: String? = null
    private var msgLoad = ""
    private var jobSearch: Job? = null

    private val storage = SearchStorage()
    private val engine = SearchEngine(
        storage = storage,
        pages = PageStorage(),
        parent = this
    )
    private var blockedNotify = false

    //last:
    private var lastProcent: Int = 0
    private var lastCount: Int = 0
    private var lastTime: Long = 0

    fun init(context: Context) {
        if (isInit) return
        helper = SearchHelper(context)
        strings = SearchStrings(
            format_search_date = context.getString(R.string.format_search_date),
            format_search_proc = context.getString(R.string.format_search_proc),
            format_month_no_loaded = context.getString(R.string.format_month_no_loaded),
            format_page_no_loaded = context.getString(R.string.format_page_no_loaded),
            format_load = context.getString(R.string.format_load),
            not_found = context.getString(R.string.not_found),
            search_in_results = context.getString(R.string.search_in_results),
            search_mode = context.resources.getStringArray(R.array.search_mode),
            format_found = context.getString(R.string.format_found),
        )
        isInit = true
    }

    override suspend fun doLoad() {
        isExport = false
        loadDate?.let { date ->
            val d = dateFromString(date)
            msgLoad = String.format(strings.format_load, d.monthString + " " + d.year)
            postState(NeoState.Message(msgLoad))
            val masterLoader = MasterLoader(this)
            currentLoader = masterLoader
            masterLoader.loadMonth(d.month, d.year)
            val calendarLoader = CalendarLoader()
            calendarLoader.setDate(d.year, d.month)
            val links = calendarLoader.getLinkList() //.sorted()
            val pageLoader = PageLoader()
            currentLoader = pageLoader
            storage.deleteByLink(date)
            for (link in links) {
                pageLoader.download(link, false)
                if (isRun.not()) break
            }
            pageLoader.finish()
            engine.startSearch(date)
            notifyResult()
            loadDate = null
        }
        loadLink?.let { link ->
            postState(NeoState.Message(String.format(strings.format_load, link)))
            val id = storage.getIdByLink(link)
            storage.delete(id.toString())
            val pageLoader = PageLoader()
            currentLoader = pageLoader
            pageLoader.download(link, true)
            val item = engine.findInPage(link, id)
            postState(NeoState.ListValue(listOf(item)))
            loadLink = null
        }
    }

    override fun onDestroy() {
        factory.destroy()
        storage.close()
    }

    override fun cancel() {
        engine.stop()
        jobSearch?.cancel()
        super.cancel()
    }

    override fun getInputData(): Data {
        if (isExport) {
            return Data.Builder()
                .putString(Const.TASK, Const.SEARCH + ".Export")
                .build()
        }
        loadDate?.let {
            val d = Data.Builder()
                .putString(Const.TASK, Const.SEARCH)
                .putString(Const.TIME, it)
                .build()
            loadDate = null
            return d
        }
        loadLink?.let {
            val d = Data.Builder()
                .putString(Const.TASK, Const.SEARCH)
                .putString(Const.LINK, it)
                .build()
            loadLink = null
            return d
        }
        return Data.Builder()
            .putString(Const.TASK, Const.SEARCH)
            .putString(Const.STRING, helper.request)
            .putString(Const.START, helper.start.my)
            .putString(Const.END, helper.end.my)
            .build()
    }

    fun paging(page: Int, pager: NeoPaging.Pager): Flow<PagingData<ListItem>> {
        paging.setPager(pager)
        return paging.run(page)
    }

    override val pagingScope: CoroutineScope
        get() = scope

    override suspend fun postFinish() {
        postState(NeoState.Ready)
    }

    override fun postError(error: Exception) {
        setState(NeoState.Error(error, getInputData()))
    }

    fun setEndings(context: Context) {
        if (engine.endings == null)
            engine.endings = context.resources.getStringArray(R.array.endings)
    }

    fun loadMonth(date: String) { //MM.yy
        loadDate = date
        if (checkConnect())
            load()
    }

    fun loadPage(link: String) {
        loadLink = link
        if (checkConnect())
            load()
    }

    fun startSearch(request: String, mode: Int) {
        if (isRun) cancel()
        isExport = false
        helper.request = request
        jobSearch = scope.launch {
            isRun = true
            labelMode = if (mode >= SearchEngine.MODE_RESULT_TEXT)
                strings.search_in_results
            else
                strings.search_mode[mode]
            helper.countMaterials = 0
            notifyResult()
            shownResult = true
            engine.startSearch(mode)
            isRun = false
            notifyResult()
        }
    }

    fun showLastResult() {
        shownResult = true
        scope.launch {
            if (helper.countMaterials == 0) {
                val cursor = storage.getResults(helper.isDesc)
                helper.countMaterials = if (cursor.moveToFirst())
                    cursor.count else 0
                cursor.close()
            }
            factory.total = helper.countMaterials
            postState(NeoState.Success)
        }
    }

    private fun dateFromString(date: String): DateUnit {
        val month = date.substring(0, 2).toInt()
        val year = date.substring(3).toInt() + 2000
        return DateUnit.putYearMonth(year, month)
    }

    override suspend fun notifyResult() {
        if (blockedNotify) {
            blockedNotify = false
            return
        }
        setLabel()
        factory.total = helper.countMaterials
        postState(NeoState.Success)
    }

    private fun setLabel() = helper.run {
        label = String.format(
            strings.format_found,
            labelMode.substring(labelMode.indexOf(" ") + 1),
            request,
            engine.countMatches,
            countMaterials,
            optionsString
        )
    }

    override suspend fun notifyDate(d: DateUnit) {
        if (blockedNotify) {
            blockedNotify = false
            return
        }
        postState(
            NeoState.Message(
                String.format(
                    strings.format_search_date,
                    d.monthString, d.year
                )
            )
        )
    }

    override fun clearLast() {
        lastProcent = -1
        lastCount = 0
        lastTime = 0
    }

    override suspend fun notifySpecialEvent(e: Long) {
        blockedNotify = true
        postState(NeoState.LongValue(e))
    }

    override suspend fun searchFinish() {
        setLabel()
        helper.saveLastResult()
    }

    override suspend fun notifyPercent(p: Int) {
        if (blockedNotify) {
            blockedNotify = false
            return
        }
        if (lastProcent < p) {
            lastProcent = p
            postState(NeoState.Message(String.format(strings.format_search_proc, p)))
        } else {
            val now = System.currentTimeMillis()
            if (helper.countMaterials - lastCount > NeoPaging.ON_PAGE &&
                now - lastTime > SearchEngine.DELAY_UPDATE
            ) {
                lastTime = now
                notifyResult()
                lastCount = helper.countMaterials
            }
        }
    }

    fun existsResults(): Boolean {
        storage.open()
        val cursor = storage.getResults(false)
        val result = cursor.moveToFirst()
        cursor.close()
        return result
    }

    override fun postPercent(value: Int) {
        setState(NeoState.Message("$msgLoad ($value%)"))
    }

    fun clearBase() {
        storage.clear()
    }

    fun startExport(file: String) {
        isExport = true
        isRun = true
        scope.launch {
            doExport(Uri.parse(file))
            postState(NeoState.Message(file))
            isRun = false
        }
    }

    private fun doExport(file: Uri) {
        val outStream = App.context.contentResolver.openOutputStream(file)
        val bw = BufferedWriter(OutputStreamWriter(outStream, Const.ENCODING))
        bw.write("<html><head><meta http-equiv='Content-Type' content='text/html; charset=windows-1251'><title>")
        bw.write(App.context.getString(R.string.search_results))
        bw.write("</title><style type='text/css'>td{max-width:900px;text-align:justify;padding:10px;")
        bw.write("border:solid 1px}a{color:#000;font-weight:bold}div{position:fixed;")
        bw.write("background-color:#fff;border:solid 1px;align-items:center;")
        bw.write("left:5px;right:5px}</style>")
        bw.flush()
        val cursor = storage.getResults(helper.isDesc)
        if (!cursor.moveToFirst()) {
            bw.write("</head><body><div style='padding:10px;top:5px'>")
            bw.write(helper.label)
            bw.write("</div></body></html>")
            cursor.close()
            bw.close()
            return
        }
        bw.write("<script>var data=[")
        val iTitle = cursor.getColumnIndex(Const.TITLE)
        val iLink = cursor.getColumnIndex(Const.LINK)
        val iDes = cursor.getColumnIndex(Const.DESCTRIPTION)
        var i: Int
        var s: String
        var link: String
        var title: String
        val isDoctrine = cursor.getString(iLink).contains(Const.DOCTRINE)
        do {
            link = cursor.getString(iLink)
            title = cursor.getString(iTitle)
            if (isDoctrine || (link.length > 5 && !title.contains(Const.HTML))) {
                bw.write("\"<tr><td><a target='_blank' href='")
                if (isDoctrine) {
                    title = App.context.getString(R.string.doctrine_pages) +
                            link.substring(Const.DOCTRINE.length)
                    bw.write(NetConst.DOCTRINE_SITE)
                } else bw.write(getUrl(link))
                bw.write("'>")
                s = ""
                if (iDes != -1) cursor.getString(iDes)?.let {
                    s = it.replace("</div>", "")
                    i = s.indexOf("<div")
                    while (i > -1) {
                        s = s.substring(0, i) + s.substring(s.indexOf(">", i) + 1)
                        i = s.indexOf("<div")
                    }
                    if (!s.contains("<p")) s = "<p>$s</p>"
                }
                s = "$title</a>$s".replace("99ccff", "1040FF")
                bw.write(s.replace("\"", "\\\""))
                bw.write("</td></tr>\",")
                bw.newLine()
                bw.flush()
            }
        } while (cursor.moveToNext() && isRun)
        cursor.close()
        val count = when (engine.mode) {
            SearchEngine.MODE_TITLES -> 20
            SearchEngine.MODE_LINKS -> 15
            else -> 10
        }
        bw.write("\"\"]; function open(p) {var q=$count; var s=''; var a=p*q;var max=a+q; if(max>data.length) ")
        bw.write("max=data.length; for(var i=a;i<max;i++) s+=data[i];document.getElementById('content').innerHTML=s;")
        bw.write(" s=''; var n=0; var x=a-q*4; while(x<0)x+=q; for(var i=x;n<9 && i<data.length;i+=q,n++) { p=i/q;")
        bw.write("if(a==i) s+='<font color=\"#1040FF\"><b>'+(p+1)+'</b></font> '; else s+='<a style=\"padding:20px\" ")
        bw.write("href=\"javascript:paging('+p+')\">'+(p+1)+'</a> ';} document.getElementById('pages').innerHTML=s;}")
        bw.flush()
        bw.write("function paging(a) {var s=window.location.href; if(s.indexOf('content')==0){open(a);")
        bw.write(" window.scrollTo({ top: 0, behavior: 'smooth' }); return;}")
        bw.write("var n=s.indexOf('?'); if(n>-1) s=s.substring(0,n); window.location.href=s+'?'+a;}")
        bw.write("function load() {var s=document.URL; var i=s.indexOf('?'); if(i>-1) i=s.substring(i+1); else i=0; open(i);}")
        bw.flush()
        bw.write("</script></head><body onload='load()'><div style='padding:10px;top:5px'>")
        bw.write(helper.label)
        bw.write("</div>")
        bw.newLine()
        bw.write("<center><table id='content' style='margin-top:100px;margin-bottom:75px'>")
        bw.write("</table></center>")
        bw.newLine()
        bw.write("<div id='pages' style='display:flex;justify-content:space-around;bottom:5px'>")
        bw.write("</div></body></html>")
        bw.close()
    }

    private fun getUrl(link: String): String {
        val url = link.replace("print/", "")
        val s = if (url.contains("/"))
            url.substring(0, url.lastIndexOf("/"))
        else url
        val i = if (s.isDigitsOnly()) s.toInt() else 2016
        return if (i < 2016) NetConst.CAB_SITE + url
        else NetConst.SITE + url

    }
}