package ru.neosvet.vestnewage.viewmodel

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.core.text.isDigitsOnly
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.work.Data
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.SearchScreen
import ru.neosvet.vestnewage.helper.SearchHelper
import ru.neosvet.vestnewage.loader.CalendarLoader
import ru.neosvet.vestnewage.loader.MasterLoader
import ru.neosvet.vestnewage.loader.basic.LoadHandlerLite
import ru.neosvet.vestnewage.loader.page.PageLoader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.SearchStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.SearchEngine
import ru.neosvet.vestnewage.view.list.paging.NeoPaging
import ru.neosvet.vestnewage.view.list.paging.SearchFactory
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.basic.SearchStrings
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.ListState
import ru.neosvet.vestnewage.viewmodel.state.SearchState
import java.io.BufferedWriter
import java.io.OutputStreamWriter

class SearchToiler : NeoToiler(), NeoPaging.Parent, SearchEngine.Parent, LoadHandlerLite {
    private val paging = NeoPaging(this)
    val isLoading: Boolean
        get() = paging.isPaging
    override lateinit var strings: SearchStrings
        private set
    private var labelMode = ""
    private var iMode = -1
    private lateinit var helper: SearchHelper
    private var loadDate: String? = null
    private var loadLink: String? = null
    private var msgLoad = ""

    private val storage = SearchStorage()
    private lateinit var engine: SearchEngine
    private var isExport = false
    private var dateSearch = DateUnit.initToday()

    //last:
    private var lastPercent = -1
    private var lastTimeS = 0L
    private var lastTimeR = 0L
    private var isWaitS = false
    private var isWaitR = false
    private var jobResults: Job? = null
    private var jobTitle: Job? = null

    val isTelegram
        get() = iMode == SearchEngine.MODE_TELEGRAM

    override fun init(context: Context) {
        helper = SearchHelper(context)
        if (labelMode.isNotEmpty()) { //from argument
            helper.mode = iMode
            helper.request = labelMode
            lastPercent = -1
            labelMode = ""
        }
        engine = SearchEngine(
            storage = storage,
            helper = helper,
            parent = this
        )
        strings = SearchStrings(
            formatDate = context.getString(R.string.format_search_date),
            formatProc = context.getString(R.string.format_search_proc),
            formatMonthNoLoaded = context.getString(R.string.format_month_no_loaded),
            formatPageNoLoaded = context.getString(R.string.format_page_no_loaded),
            formatLoad = context.getString(R.string.format_load),
            notFound = context.getString(R.string.not_found),
            searchInResults = context.getString(R.string.search_in_results),
            listMode = context.resources.getStringArray(R.array.search_mode).toList(),
            formatFound = context.getString(R.string.format_found),
        )
    }

    override suspend fun defaultState() {
        postState(SearchState.Primary(helper))
        postState(
            SearchState.Status(
                screen = if (existsResults()) SearchScreen.DEFAULT else SearchScreen.EMPTY,
                settings = null,
                shownAddition = false,
                firstPosition = -1
            )
        )
        if (helper.request.isNotEmpty())
            postState(SearchState.Start)
    }

    override suspend fun doLoad() {
        val client = NeoClient()
        val pageLoader = PageLoader(client)
        loadDate?.let { date ->
            val d = dateFromString(date)
            msgLoad = String.format(strings.formatLoad, d.monthString + " " + d.year)
            postState(BasicState.Message(msgLoad))
            val masterLoader = MasterLoader(this)
            currentLoader = masterLoader
            masterLoader.loadMonth(d.month, d.year)
            val calendarLoader = CalendarLoader(client)
            calendarLoader.setDate(d.year, d.month)
            val links = calendarLoader.getLinkList() //.sorted()
            currentLoader = pageLoader
            storage.deleteByLink(date)
            for (link in links) {
                pageLoader.download(link, false)
                if (isRun.not()) break
            }
            pageLoader.finish()
            engine.startSearch(date)
            loadDate = null
        }
        loadLink?.let { link ->
            postState(BasicState.Message(String.format(strings.formatLoad, link)))
            val id = storage.getIdByLink(link)
            storage.delete(id.toString())
            currentLoader = pageLoader
            pageLoader.download(link, true)
            val item = engine.findInPage(link, id)
            postState(ListState.Update(id, item))
            postState(BasicState.Success)
            loadLink = null
        }
    }

    override fun onDestroy() {
        storage.close()
    }

    override fun cancel() {
        engine.stop()
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
                .putString(Const.TASK, Const.SEARCH + ".LoadMonth")
                .putString(Const.TIME, it)
                .build()
            loadDate = null
            return d
        }
        loadLink?.let {
            val d = Data.Builder()
                .putString(Const.TASK, Const.SEARCH + ".LoadPage")
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

    fun paging(page: Int, pager: NeoPaging.Pager): Flow<PagingData<BasicItem>> {
        paging.setPager(pager)
        return paging.run(page)
    }

    //------begin    NeoPaging.Parent
    override val factory: SearchFactory by lazy {
        SearchFactory(storage, paging)
    }
    override val isBusy: Boolean
        get() = isRun
    override val pagingScope: CoroutineScope
        get() = scope

    override suspend fun postFinish() {
        postState(BasicState.Ready)
    }

    override fun postError(error: BasicState.Error) {
        setState(error)
    }
//------end    NeoPaging.Parent

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
        isRun = true
        helper.request = request
        iMode = mode
        labelMode = if (mode >= SearchEngine.MODE_RESULT_TEXT)
            strings.searchInResults
        else strings.listMode[mode]
        scope.launch {
            engine.startSearch(mode)
            isRun = false
        }
    }

    fun showLastResult() {
        scope.launch {
            iMode = -1
            val count = getResultCount()
            storage.isDesc = helper.isDesc
            if (count > 0) {
                helper.loadLastResult()
                for (i in strings.listMode.indices) {
                    val s = strings.listMode[i].substring(strings.listMode[i].indexOf(" ") + 1)
                    if (helper.label.contains(s)) {
                        iMode = i
                        break
                    }
                }
            }
            postState(SearchState.Results(count, true))
        }
    }

    private fun getResultCount(): Int {
        val cursor = storage.getResults(helper.isDesc)
        val count = cursor.count
        cursor.close()
        factory.total = count
        return count
    }

    private fun dateFromString(date: String): DateUnit {
        val month = date.take(2).toInt()
        val year = date.substring(3).toInt() + 2000
        return DateUnit.putYearMonth(year, month)
    }

    override suspend fun searchFinish() {
        jobResults?.cancel()
        val count = getResultCount()
        setLabel(count)
        helper.saveLastResult()
        isRun = false
        postState(SearchState.Results(count, true))
    }

    override suspend fun notifyResult() {
        setLabel(engine.countMaterials)
        factory.total = engine.countMaterials
        if (System.currentTimeMillis() - lastTimeR < 1500) {
            if (isWaitR) return
            isWaitR = true
            jobResults = viewModelScope.launch {
                delay(1500)
                postState(SearchState.Results(engine.countMaterials, false))
                lastTimeR = System.currentTimeMillis()
                isWaitR = false
            }
        } else {
            postState(SearchState.Results(engine.countMaterials, false))
            lastTimeR = System.currentTimeMillis()
        }
    }

    private fun setLabel(countMaterials: Int) = helper.run {
        label = String.format(
            strings.formatFound,
            labelMode.substring(labelMode.indexOf(" ") + 1),
            request,
            engine.countMatches,
            countMaterials,
            optionsString
        )
    }

    override suspend fun notifyDate(date: DateUnit) {
        if (System.currentTimeMillis() - lastTimeS < 250) {
            dateSearch = date
            if (isWaitS) return
            isWaitS = true
            viewModelScope.launch {
                delay(250)
                if (isRun) postStatus(dateSearch)
                isWaitS = false
            }
            return
        }
        if (date != dateSearch)
            postStatus(date)
    }

    private suspend fun postStatus(date: DateUnit) {
        postState(
            BasicState.Message(
                String.format(
                    strings.formatDate,
                    date.monthString, date.year
                )
            )
        )
        lastTimeS = System.currentTimeMillis()
    }

    override fun clearLast() {
        lastPercent = -1
        lastTimeS = 0
        lastTimeR = 0
    }

    override suspend fun notifyNotFound() {
        postState(BasicState.Empty)
    }

    override suspend fun notifyPercent(percent: Int) {
        if (lastPercent < percent) {
            lastPercent = percent
            if (System.currentTimeMillis() - lastTimeS < 250) {
                if (isWaitS) return
                isWaitS = true
                viewModelScope.launch {
                    delay(250)
                    postStatus(percent)
                    isWaitS = false
                }
            } else
                postStatus(percent)
        }
    }

    private suspend fun postStatus(percent: Int) {
        postState(BasicState.Message(String.format(strings.formatProc, percent)))
        lastTimeS = System.currentTimeMillis()
    }

    private fun existsResults(): Boolean {
        storage.open()
        val cursor = storage.getResults(false)
        val result = cursor.moveToFirst()
        cursor.close()
        return result
    }

    override fun postPercent(value: Int) {
        if (isRun) setState(BasicState.Message("$msgLoad ($value%)"))
    }

    fun clearBase() {
        storage.clear()
    }

    fun startExport(file: String) {
        isExport = true
        isRun = true
        scope.launch {
            doExport(file.toUri())
            postState(SearchState.FinishExport(file))
            isRun = false
            isExport = false
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
        val iDes = cursor.getColumnIndex(Const.DESCRIPTION)
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
                    bw.write(Urls.DoctrineSite)
                } else bw.write(getUrl(link))
                bw.write("'>")
                s = ""
                if (iDes != -1) cursor.getString(iDes)?.let {
                    s = it.replace("</div>", "")
                    i = s.indexOf("<div")
                    while (i > -1) {
                        s = s.take(i) + s.substring(s.indexOf(">", i) + 1)
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
        val s = if (link.contains("/"))
            link.take(link.lastIndexOf("/"))
        else link
        val i = if (s.isDigitsOnly()) s.toInt() else 2016
        return if (i < 2016) Urls.MainSite + link
        else Urls.Site + link
    }

    fun setArguments(mode: Int, request: String) {
        iMode = mode
        labelMode = request
    }

    fun getTitleOn(position: Int) {
        jobTitle?.cancel()
        jobTitle = scope.launch {
            val title = storage.getTitle(position)
            postState(BasicState.Message("%$title"))
        }
    }
}