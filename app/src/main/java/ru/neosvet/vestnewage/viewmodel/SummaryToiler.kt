package ru.neosvet.vestnewage.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.work.Data
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
import ru.neosvet.vestnewage.utils.Files
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
    private var sBack: String = ""
    private var isOpened = false
    private val storage: AdditionStorage by lazy {
        AdditionStorage()
    }
    private val paging: NeoPaging by lazy {
        NeoPaging(this)
    }
    private val client = NeoClient()
    private var jobTime: Job? = null

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
        when (selectedTab) {
            SummaryTab.RSS -> {
                val loader = SummaryLoader(client)
                loader.load()
                openFile(Files.RSS)
            }

            SummaryTab.ADDITION -> {
                val loader = AdditionLoader(client)
                loader.load(storage, 0)
                openAddition()
            }

            SummaryTab.DOCTRINE -> {
                val loader = SummaryLoader(client)
                loader.loadDoctrine()
                openFile(Files.DOCTRINE)
            }
        }
    }

    override fun onDestroy() {
        if (isOpened)
            storage.close()
    }

    private fun isNeedReload(): Boolean {
        val f = when (selectedTab) {
            SummaryTab.RSS -> Files.file(Files.RSS)
            SummaryTab.ADDITION -> Files.dateBase(DataBase.ADDITION)
            SummaryTab.DOCTRINE -> Files.file(Files.DOCTRINE)
        }
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
            val isEmpty = when (selectedTab) {
                SummaryTab.RSS -> !openFile(Files.RSS)
                SummaryTab.ADDITION -> !openAddition()
                SummaryTab.DOCTRINE -> !openFile(Files.DOCTRINE)
            }
            if (loadIfNeed && (isEmpty || isNeedReload())) {
                reLoad()
                if (selectedTab == SummaryTab.ADDITION) {
                    val f = Files.dateBase(DataBase.ADDITION)
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

    private suspend fun openFile(name: String): Boolean {
        clearStates()
        val file = Files.file(name)
        if (!file.exists()) return false
        val list = mutableListOf<BasicItem>()
        val now = System.currentTimeMillis()
        val br = BufferedReader(FileReader(file))
        var s: String? = br.readLine()
        var d: String
        val isDoc = selectedTab == SummaryTab.DOCTRINE
        while (s != null) {
            val link = br.readLine()
            val item = BasicItem(s, link)
            d = br.readLine()
            if (isDoc) {
                item.addHead(s)
                s = br.readLine()
                while (s != Const.END) {
                    d += s
                    s = br.readLine()
                }
                scanLink(item, d)
            }
            val time = br.readLine()
            item.des = DateUnit.getDiffDate(now, time.toLong()) +
                    sBack + Const.N + d
            list.add(item)
            s = br.readLine()
        }
        br.close()
        postState(ListState.Primary(file.lastModified(), list))
        if (list.isEmpty())
            return false
        if (isRun && selectedTab == SummaryTab.RSS) loadPages(list)
        return true
    }

    private fun scanLink(item: BasicItem, s: String) {
        var a = s.indexOf("href") + 6
        var b: Int
        while (a > 5) {
            b = s.indexOf("\"", a)
            val link = s.substring(a, b)
            a = s.indexOf("<", b)
            item.addLink(s.substring(b + 2, a), link)
            a = s.indexOf("href", a) + 6
        }
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
        SummaryTab.ADDITION.value -> SummaryTab.ADDITION
        else -> SummaryTab.DOCTRINE
    }

    fun getTimeOn(position: Int) {
        jobTime?.cancel()
        jobTime = scope.launch {
            val time = storage.getTime(position)
            postState(BasicState.Message(time))
        }
    }


    fun shareItem(id: String) {
        scope.launch {
            storage.getItem(id, true)?.let {
                postState(ListState.Update(0, it))
            }
        }
    }
}