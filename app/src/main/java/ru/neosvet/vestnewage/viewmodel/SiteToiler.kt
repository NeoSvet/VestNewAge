package ru.neosvet.vestnewage.viewmodel

import android.content.Context
import androidx.work.Data
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.SiteTab
import ru.neosvet.vestnewage.loader.SiteLoader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.AdsStorage
import ru.neosvet.vestnewage.utils.AdsUtils
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.ListState
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

class SiteToiler : NeoToiler() {
    companion object {
        const val MAIN = "/main"
        const val NEWS = "/news"
        const val END = "<end>"
    }

    private var selectedTab = SiteTab.NEWS
    private var novosti = ""
    private val file: File
        get() = Lib.getFile(if (selectedTab == SiteTab.NEWS) NEWS else MAIN)
    private val client = NeoClient(NeoClient.Type.SECTION)
    private val url: String
        get() = if (selectedTab == SiteTab.SITE)
            Urls.Site
        else Urls.Ads
    private val storage: AdsStorage by lazy {
        AdsStorage()
    }
    private val ads: AdsUtils by lazy {
        AdsUtils(storage)
    }

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, "Site")
        .putString(Const.TAB, selectedTab.toString())
        .putString(Const.LINK, url)
        .build()

    override fun init(context: Context) {
        novosti = context.getString(R.string.novosti)
    }

    override suspend fun defaultState() {
        openList(true)
    }

    override suspend fun doLoad() {
        if (selectedTab == SiteTab.DEV)
            loadAds(true)
        else
            loadList()
    }

    override fun onDestroy() {
        storage.close()
    }

    private suspend fun loadList() {
        val loader = SiteLoader(client, file.toString())
        val list = loader.load(url) as MutableList
        if (selectedTab == SiteTab.SITE)
            list.add(0, getNovosti())
        postState(ListState.Primary(file.lastModified(), list))
    }

    private fun getNovosti(): BasicItem {
        return BasicItem(novosti).apply { addLink(Urls.News) }
    }

    private suspend fun loadAds(reload: Boolean) {
        if (reload) ads.loadAds(client)
        val list = ads.loadList(false)
        postState(ListState.Primary(ads.time, list))
    }

    fun openList(loadIfNeed: Boolean, tab: Int = -1) {
        this.loadIfNeed = loadIfNeed
        if (tab != -1)
            selectedTab = convertTab(tab)
        scope.launch {
            if (selectedTab == SiteTab.DEV)
                loadAds(false)
            else
                openFile()
        }
    }

    private suspend fun openFile() {
        val f = file
        if (f.exists().not()) {
            postState(BasicState.NotLoaded)
            reLoad()
            return
        }
        val list = mutableListOf<BasicItem>()
        if (selectedTab == SiteTab.SITE)
            list.add(getNovosti())
        var i = 0
        var d: String?
        var l: String
        var h: String
        val br = BufferedReader(FileReader(f))
        var t: String? = br.readLine()
        while (t != null) {
            d = br.readLine()
            l = br.readLine()
            h = if (l != END) br.readLine() else END
            if (l == "#") {
                list.add(BasicItem(t, true))
            } else {
                list.add(BasicItem(t).apply { des = d })
                if (h != END) {
                    list[i].addLink(h, l)
                    l = br.readLine()
                    while (l != END) {
                        h = br.readLine()
                        list[i].addLink(h, l)
                        l = br.readLine()
                    }
                } else list[i].addLink("", l)
            }
            i++
            t = br.readLine()
        }
        br.close()
        postState(ListState.Primary(f.lastModified(), list))

        if (loadIfNeed && DateUnit.isLongAgo(f.lastModified())) {
            reLoad()
        }
    }

    fun readAds(item: BasicItem) {
        storage.setRead(item)
    }

    fun setArgument(tab: Int) {
        selectedTab = convertTab(tab)
    }

    private fun convertTab(tab: Int) = when (tab) {
        SiteTab.NEWS.value -> SiteTab.NEWS
        SiteTab.SITE.value -> SiteTab.SITE
        else -> SiteTab.DEV
    }
}