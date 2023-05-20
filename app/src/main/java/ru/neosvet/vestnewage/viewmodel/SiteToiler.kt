package ru.neosvet.vestnewage.viewmodel

import androidx.work.Data
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.loader.SiteLoader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.AdsStorage
import ru.neosvet.vestnewage.utils.AdsUtils
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.basic.SiteStrings
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

class SiteToiler : NeoToiler() {
    companion object {
        const val TAB_NEWS = 0
        const val TAB_SITE = 1
        const val TAB_DEV = 2
        const val MAIN = "/main"
        const val NEWS = "/news"
        const val END = "<end>"
    }

    private val strings = SiteStrings(
        news_dev = App.context.getString(R.string.news_dev),
        novosti = App.context.getString(R.string.novosti),
        back_title = App.context.getString(R.string.back_title),
        back_des = App.context.getString(R.string.back_des)
    )
    var selectedTab = TAB_NEWS
    private val nameFiles = arrayOf(NEWS, MAIN)
    private val file: File
        get() = Lib.getFile(nameFiles[selectedTab])
    private val client = NeoClient(NeoClient.Type.SECTION)
    private val url: String
        get() = if (selectedTab == TAB_SITE)
            Urls.Site
        else
            Urls.Ads
    private val storage: AdsStorage by lazy {
        AdsStorage()
    }
    private val ads: AdsUtils by lazy {
        AdsUtils(storage)
    }
    val isDevTab: Boolean
        get() = selectedTab == TAB_DEV
    val isNewsTab: Boolean
        get() = selectedTab == TAB_NEWS
    val isSiteTab: Boolean
        get() = selectedTab == TAB_SITE

    override fun onDestroy() {
        storage.close()
    }

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, "Site")
        .putInt(Const.TAB, selectedTab)
        .putString(Const.LINK, url)
        .build()

    override suspend fun doLoad() {
        if (selectedTab == TAB_DEV)
            loadAds(true)
        else
            loadList()
    }

    private suspend fun loadList() {
        val loader = SiteLoader(client, file.toString())
        val list = loader.load(url) as MutableList
        postState(NeoState.LongValue(file.lastModified()))
        list.add(0, getFirstItem())
        postState(NeoState.ListValue(list))
    }

    private suspend fun loadAds(reload: Boolean) {
        if (reload) ads.loadAds(client)
        postState(NeoState.LongValue(ads.time))
        val list = ads.loadList(false)
        list.add(0, getFirstItem())
        postState(NeoState.ListValue(list))
    }

    fun openList(loadIfNeed: Boolean) {
        this.loadIfNeed = loadIfNeed
        scope.launch {
            val f = file
            if (f.exists().not()) {
                postState(NeoState.LongValue(0))
                reLoad()
                return@launch
            }
            postState(NeoState.LongValue(f.lastModified()))
            val list = mutableListOf<ListItem>()
            list.add(getFirstItem())
            var i = 1
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
                    list.add(ListItem(t, true))
                } else {
                    list.add(ListItem(t).apply { des = d })
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
            postState(NeoState.ListValue(list))

            if (loadIfNeed && DateUnit.isLongAgo(f.lastModified())) {
                reLoad()
            }
        }
    }

    private fun getFirstItem(): ListItem =
        when (selectedTab) {
            TAB_NEWS -> ListItem(strings.news_dev).apply { addLink("") }
            TAB_SITE -> ListItem(strings.novosti).apply { addLink(Urls.News) }
            else -> ListItem(strings.back_title).apply { des = strings.back_des } //TAB_DEV
        }

    fun openAds() {
        loadIfNeed = false
        scope.launch {
            loadAds(false)
        }
    }

    fun readAds(item: ListItem) {
        storage.setRead(item)
    }
}