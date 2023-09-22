package ru.neosvet.vestnewage.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.work.Data
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.SummaryTab
import ru.neosvet.vestnewage.helper.SummaryHelper
import ru.neosvet.vestnewage.loader.AdditionLoader
import ru.neosvet.vestnewage.loader.SummaryLoader
import ru.neosvet.vestnewage.loader.page.PageLoader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.storage.AdditionStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.percent
import ru.neosvet.vestnewage.view.list.paging.AdditionFactory
import ru.neosvet.vestnewage.view.list.paging.NeoPaging
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.ListState
import java.io.BufferedReader
import java.io.FileReader

class SummaryToiler : NeoToiler(), NeoPaging.Parent {
    private var selectedTab = SummaryTab.RSS
    private val isRss: Boolean
        get() = selectedTab == SummaryTab.RSS
    private var sBack: String = ""
    private var isOpened = false
    private val storage: AdditionStorage by lazy {
        AdditionStorage()
    }
    private val paging: NeoPaging by lazy {
        NeoPaging(this)
    }
    private val client = NeoClient(NeoClient.Type.SECTION)

    val isLoading: Boolean
        get() = paging.isPaging

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, SummaryHelper.TAG)
        .putString(Const.TAB, selectedTab.toString())
        .build()

    override fun init(context: Context) {
        sBack = context.getString(R.string.back)
    }

    override suspend fun defaultState() {
        openList(true)
    }

    override suspend fun doLoad() {
        if (isRss) {
            val loader = SummaryLoader(client)
            loader.load()
            val summaryHelper = SummaryHelper()
            summaryHelper.updateBook()
            openRss()
        } else {
            val loader = AdditionLoader(client)
            loader.load(storage, 0)
            openAddition()
        }
    }

    override fun onDestroy() {
        if (isOpened)
            storage.close()
    }

    private fun isNeedReload(): Boolean {
        val f = if (isRss) Lib.getFile(Const.RSS) else Lib.getFileDB(DataBase.ADDITION)
        return !f.exists() || DateUnit.isLongAgo(f.lastModified())
    }

    private suspend fun loadPages(pages: List<BasicItem>) {
        val loader = PageLoader(client)
        currentLoader = loader
        var i = 0
        while (i < pages.size && isRun) {
            loader.download(pages[i].link, false)
            i++
            postState(BasicState.Progress(i.percent(pages.size)))
        }
        loader.finish()
        isRun = false
        postState(BasicState.Success)
    }

    fun openList(loadIfNeed: Boolean, tab: Int = -1) {
        this.loadIfNeed = loadIfNeed
        if (tab != -1)
            selectedTab = convertTab(tab)
        scope.launch {
            val isEmpty = if (isRss) !openRss()
            else !openAddition()

            if (loadIfNeed && (isEmpty || isNeedReload())) {
                reLoad()
                if (isRss.not()) {
                    val f = Lib.getFileDB(DataBase.ADDITION)
                    f.setLastModified(System.currentTimeMillis())
                }
            }
        }
    }

    private suspend fun openAddition(): Boolean {
        clearPrimaryState()
        storage.open()
        isOpened = true
        storage.findMax()
        factory.total = storage.max
        postState(ListState.Paging(storage.max))
        return storage.max != 0
    }

    private suspend fun openRss(): Boolean {
        clearStates()
        val list = mutableListOf<BasicItem>()
        val now = System.currentTimeMillis()
        val file = Lib.getFile(Const.RSS)
        val br = BufferedReader(FileReader(file))
        var title: String? = br.readLine()
        var d: String
        while (title != null) {
            val link = br.readLine()
            d = br.readLine()
            val time = br.readLine()
            d = DateUnit.getDiffDate(now, time.toLong()) +
                    sBack + Const.N + d
            list.add(BasicItem(title, link).apply {
                des = d
            })
            title = br.readLine()
        }
        br.close()
        postState(ListState.Primary(file.lastModified(), list))
        if (list.isEmpty())
            return false
        if (isRun) loadPages(list)
        return true
    }

    fun paging(page: Int, pager: NeoPaging.Pager): Flow<PagingData<BasicItem>> {
        paging.setPager(pager)
        return paging.run(page)
    }

    //------begin    NeoPaging.Parent
    override val factory: AdditionFactory by lazy {
        AdditionFactory(storage, paging)
    }
    override val isBusy: Boolean
        get() = isRun
    override val pagingScope: CoroutineScope
        get() = viewModelScope

    override suspend fun postFinish() {
        if (storage.max > 0)
            postState(BasicState.Ready)
    }
//------end    NeoPaging.Parent

    override fun postError(error: BasicState.Error) {
        isRun = false
        setState(error)
    }

    fun setArgument(tab: Int) {
        selectedTab = convertTab(tab)
    }

    private fun convertTab(tab: Int) = when (tab) {
        SummaryTab.RSS.value -> SummaryTab.RSS
        else -> SummaryTab.ADDITION
    }
}