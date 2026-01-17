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
import ru.neosvet.vestnewage.storage.DevStorage
import ru.neosvet.vestnewage.storage.NewsStorage
import ru.neosvet.vestnewage.utils.AdsUtils
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Files
import ru.neosvet.vestnewage.view.list.BasicAdapter
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.basic.SiteStrings
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.ListState
import java.io.BufferedReader
import java.io.FileReader

class SiteToiler : NeoToiler() {
    companion object {
        const val MAIN = "/main"
        const val END = "<end>"
    }

    private var selectedTab = SiteTab.NEWS
    private lateinit var strings: SiteStrings
    private val client = NeoClient()
    private val newsStorage = NewsStorage()
    private val devStorage = DevStorage()
    private lateinit var ads: AdsUtils
    private val fixers = mutableListOf<Pair<String, String>>()

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, "Site")
        .putString(Const.TAB, selectedTab.toString())
        .build()

    override fun init(context: Context) {
        strings = SiteStrings(
            novosti = context.getString(R.string.novosti),
            markRead = context.getString(R.string.mark_read),
            today = context.getString(R.string.today_s),
            unread = context.getString(R.string.unread),
        )
        fixers.add(Pair("-The_Path_", context.getString(R.string.the_path)))
        fixers.add(Pair("-Timekeeping_", context.getString(R.string.timekeeping)))
        ads = AdsUtils(devStorage, context)
    }

    override suspend fun defaultState() {
        openList(true)
    }

    override suspend fun doLoad() {
        val loader = SiteLoader(client)
        when (selectedTab) {
            SiteTab.NEWS -> loader.loadAds()
            SiteTab.SITE -> loader.loadSite()
            SiteTab.DEV -> loader.loadDevAds()
        }
        openList(false)
    }

    override fun onDestroy() {
        newsStorage.close()
        devStorage.close()
    }

    private fun getNovosti(): BasicItem {
        return BasicItem(strings.novosti).apply { addLink(Urls.News) }
    }

    private suspend fun openDev() {
        if (devStorage.unreadCount > 0) {
            val list = ads.getFullList() as MutableList<BasicItem>
            list.forEach {
                if (it.des[0] == BasicAdapter.LABEL_SEPARATOR)
                    it.des = BasicAdapter.LABEL_SEPARATOR + strings.unread + it.des
            }
            list.add(0, BasicItem(strings.markRead))
            postState(ListState.Primary(devStorage.getTime(), list))
        } else {
            val list = ads.getFullList()
            if (loadIfNeed && list.isEmpty()) reLoad()
            else postState(ListState.Primary(devStorage.getTime(), list))
        }
    }

    fun openList(loadIfNeed: Boolean, tab: Int = -1) {
        this.loadIfNeed = loadIfNeed
        if (tab != -1)
            selectedTab = convertTab(tab)
        scope.launch {
            when (selectedTab) {
                SiteTab.NEWS -> openNews()
                SiteTab.SITE -> openSite()
                SiteTab.DEV -> openDev()
            }
        }
    }

    private suspend fun openNews() {
        val cursor = newsStorage.getAll()
        val list = mutableListOf<BasicItem>()
        var time = 0L
        var line: String? = null
        if (cursor.moveToFirst()) {
            val iTitle = cursor.getColumnIndex(Const.TITLE)
            val iDes = cursor.getColumnIndex(Const.DESCRIPTION)
            val iLink = cursor.getColumnIndex(Const.LINK)
            val iTime = cursor.getColumnIndex(Const.TIME)
            time = cursor.getLong(iTime)
            val today = DateUnit.initToday().toShortDateString()
            while (cursor.moveToNext()) {
                val link = cursor.getString(iLink)
                val item = BasicItem(cursor.getString(iTitle))
                item.des = fixDes(cursor.getString(iDes))
                val date = DateUnit.putMills(cursor.getLong(iTime)).toShortDateString()
                if (today == date)
                    item.des = BasicAdapter.LABEL_SEPARATOR + strings.today +
                            BasicAdapter.LABEL_SEPARATOR + item.des
                if (link.contains(Const.N)) {
                    link.lines().forEach { s ->
                        if (line != null) {
                            item.addLink(fixHead(s), line)
                            line = null
                        } else line = s
                    }
                } else if (link.isNotEmpty())
                    item.addLink(link)
                list.add(item)
            }
        }
        cursor.close()

        postState(ListState.Primary(time, list))
        if (loadIfNeed && (list.isEmpty() || DateUnit.isLongAgo(time)))
            reLoad()
    }

    private fun fixDes(s: String): String {
        if (s.contains(".jpg")) {
            var a = s.indexOf(">") + 1
            var des = s
            var b: Int
            while (a < des.length) {
                b = des.indexOf("<", a)
                if (b > a) {
                    val t = des.substring(a, b)
                    for (f in fixers) {
                        if (t.contains(f.first)) {
                            des = des.take(a) + f.second + des.substring(b)
                            b = des.indexOf("<", a)
                            break
                        }
                    }
                } else if (b == -1) break
                a = des.indexOf(">", b) + 1
            }
            return des
        }
        return s
    }

    private fun fixHead(s: String): String {
        if (s.contains(".jpg")) {
            for (f in fixers) {
                if (s.contains(f.first))
                    return f.second
            }
        }
        return s
    }

    private suspend fun openSite() {
        val f = Files.file(MAIN)
        if (f.exists().not()) {
            postState(BasicState.NotLoaded)
            reLoad()
            return
        }
        val list = mutableListOf<BasicItem>()
        list.add(getNovosti())
        var d: String?
        var l: String
        var h: String
        val br = BufferedReader(FileReader(f))
        var t: String? = br.readLine()
        while (t != null) {
            d = br.readLine()
            l = br.readLine()
            h = if (l != END) br.readLine() else END
            val item: BasicItem
            if (l == "#")
                item = BasicItem(t, true)
            else {
                item = BasicItem(t).apply { des = d }
                if (h != END) {
                    item.addLink(h, l)
                    l = br.readLine()
                    while (l != END) {
                        h = br.readLine()
                        item.addLink(h, l)
                        l = br.readLine()
                    }
                } else item.addLink("", l)
            }
            list.add(item)
            t = br.readLine()
        }
        br.close()
        postState(ListState.Primary(f.lastModified(), list))
        if (loadIfNeed && (list.size < 2 || DateUnit.isLongAgo(f.lastModified())))
            reLoad()
    }

    fun markAsRead(index: Int, item: BasicItem) {
        if (item.des[0] != BasicAdapter.LABEL_SEPARATOR) return
        scope.launch {
            item.des = item.des.substring(item.des.indexOf(BasicAdapter.LABEL_SEPARATOR, 2) + 1)
            devStorage.setRead(item.title, item.des)
            postState(ListState.Update(index, item))
        }
    }

    fun setArgument(tab: Int) {
        selectedTab = convertTab(tab)
    }

    private fun convertTab(tab: Int) = when (tab) {
        SiteTab.NEWS.value -> SiteTab.NEWS
        SiteTab.SITE.value -> SiteTab.SITE
        else -> SiteTab.DEV
    }

    fun allMarkAsRead() {
        scope.launch {
            val list = ads.getFullList()
            list.forEach {
                if (it.des[0] == BasicAdapter.LABEL_SEPARATOR) {
                    it.des = it.des.substring(1)
                    devStorage.setRead(it.title, it.des)
                }
            }
            postState(ListState.Primary(devStorage.getTime(), list))
        }
    }
}