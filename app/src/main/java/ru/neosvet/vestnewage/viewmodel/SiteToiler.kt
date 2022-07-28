package ru.neosvet.vestnewage.viewmodel

import androidx.work.Data
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.loader.SiteLoader
import ru.neosvet.vestnewage.network.NeoClient
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
        const val FORUM = "intforum.html"
        const val NOVOSTI = "novosti.html"
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
    private val url: String
        get() = if (selectedTab == TAB_SITE)
            NeoClient.SITE
        else
            NeoClient.SITE + NOVOSTI
    private val ads: AdsUtils by lazy {
        AdsUtils(App.context)
    }

    override fun onDestroy() {
        ads.close()
    }

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, "Site")
        .putString(Const.FILE, file.toString())
        .putString(Const.LINK, url)
        .build()

    override suspend fun doLoad() {
        if (selectedTab == TAB_DEV)
            loadAds()
        else
            loadList()
    }

    private suspend fun loadList() {
        val loader = SiteLoader(file.toString())
        val list = loader.load(url) as MutableList
        postState(NeoState.LongValue(file.lastModified()))
        list.add(0, getFirstItem())
        postState(NeoState.ListValue(list))
    }

    private suspend fun loadAds() {
        ads.loadAds()
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
                        if (l == END) l = ""
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
            TAB_SITE -> ListItem(strings.novosti).apply { addLink(FORUM) }
            else -> ListItem(strings.back_title).apply { des = strings.back_des } //TAB_DEV
        }

    fun openAds() {
        loadIfNeed = false
        scope.launch {
            postState(NeoState.LongValue(ads.time))
            val list = ads.loadList(false)
            list.add(0, getFirstItem())
            postState(NeoState.ListValue(list))
        }
    }

    val isDevTab: Boolean
        get() = selectedTab == TAB_DEV
    val isNewsTab: Boolean
        get() = selectedTab == TAB_NEWS
    val isSiteTab: Boolean
        get() = selectedTab == TAB_SITE
}