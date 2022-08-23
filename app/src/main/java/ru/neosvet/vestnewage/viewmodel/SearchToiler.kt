package ru.neosvet.vestnewage.viewmodel

import android.content.Context
import androidx.work.Data
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.helper.SearchHelper
import ru.neosvet.vestnewage.loader.CalendarLoader
import ru.neosvet.vestnewage.loader.MasterLoader
import ru.neosvet.vestnewage.loader.basic.LoadHandlerLite
import ru.neosvet.vestnewage.loader.page.PageLoader
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.storage.SearchStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.SearchEngine
import ru.neosvet.vestnewage.view.list.paging.NeoPaging
import ru.neosvet.vestnewage.view.list.paging.SearchFactory
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.basic.SearchStrings

class SearchToiler : NeoToiler(), NeoPaging.Parent, SearchEngine.Parent, LoadHandlerLite {
    private val paging = NeoPaging(this)
    override val factory: SearchFactory by lazy {
        SearchFactory(storage, paging)
    }
    val isLoading: Boolean
        get() = paging.isPaging
    private var isInit = false
    override lateinit var strings: SearchStrings
        private set
    private var labelMode = ""
    override lateinit var helper: SearchHelper
        private set
    var shownResult = false
        private set
    private var loadDate: String? = null
    private var loadLink: String? = null
    private var loader: MasterLoader? = null
    private var msgLoad = ""

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
        loadDate?.let { date ->
            val d = dateFromString(date)
            msgLoad = String.format(strings.format_load, d.monthString + " " + d.year)
            postState(NeoState.Message(msgLoad))
            if (loader == null)
                loader = MasterLoader(this)
            loader?.loadMonth(d.month, d.year)
            val calendar = CalendarLoader()
            calendar.setDate(d.year, d.month)
            val links = calendar.getLinkList() //.sorted()
            val pageLoader = PageLoader()
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
            pageLoader.download(link, true)
            pageLoader.finish()
            val item = engine.findInPage(link, id)
            postState(NeoState.ListValue(listOf(item)))
            loadLink = null
        }
    }

    override fun onDestroy() {
        storage.close()
    }

    override fun cancel() {
        loader?.cancel()
        engine.stop()
        super.cancel()
    }

    override fun getInputData(): Data {
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

    fun paging() = paging.run()

    override val pagingScope: CoroutineScope
        get() = scope

    override suspend fun postFinish() {
        postState(NeoState.Ready)
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
        helper.request = request
        scope.launch {
            isRun = true
            labelMode = if (mode >= SearchEngine.MODE_RESULT_TEXT)
                strings.search_in_results
            else
                strings.search_mode[mode]
            helper.countMaterials = 0
            SearchFactory.reset(0)
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
            if (helper.countMaterials - lastCount > Const.MAX_ON_PAGE &&
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
}