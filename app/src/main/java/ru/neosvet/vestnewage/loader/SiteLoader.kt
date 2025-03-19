package ru.neosvet.vestnewage.loader

import android.content.ContentValues
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.SimpleItem
import ru.neosvet.vestnewage.loader.page.PageParser
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.DataBase
import ru.neosvet.vestnewage.storage.DevStorage
import ru.neosvet.vestnewage.storage.NewsStorage
import ru.neosvet.vestnewage.storage.UnreadStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Files
import ru.neosvet.vestnewage.utils.hasDate
import ru.neosvet.vestnewage.viewmodel.SiteToiler
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class SiteLoader(
    private val client: NeoClient
) {
    private val storage: DevStorage by lazy {
        DevStorage()
    }
    private var time = -1L
    var warnIndex = -1
        private set

    fun loadSite() {
        val list = loadList(Urls.Site)
        val file = Files.file(SiteToiler.MAIN)
        val bw = BufferedWriter(OutputStreamWriter(FileOutputStream(file)))
        list.forEach { item ->
            bw.write(item.title + Const.N)
            bw.write(item.des + Const.N)
            when {
                item.hasLink() -> bw.write(item.link + Const.N)
                else -> bw.write("@" + Const.N)
            }
            bw.write(SiteToiler.END + Const.N)
            bw.flush()
        }
        bw.close()
    }

    fun loadAds() {
        val list = loadList(Urls.Ads)
        var i: Int
        var d: String
        val storage = NewsStorage()
        storage.clear()
        storage.insertTime()
        val now = DateUnit.initNow()
        var prevTime = now.timeInMills
        var date: DateUnit
        var time: Long
        var id = 2
        list.forEach { item ->
            val row = ContentValues()
            row.put(DataBase.ID, id)
            id++
            row.put(Const.TITLE, item.title)
            row.put(Const.DESCRIPTION, item.des)
            i = item.des.lastIndexOf("<p")
            date = DateUnit.putMills(prevTime)
            if (i > -1 && item.des.length - i > 10) {
                i += 3
                d = item.des.substring(i, i + 10)
                if (d.hasDate) {
                    if (d.contains("<")) {
                        i = d.indexOf("<")
                        d = d.substring(0, i)
                    }
                    date = DateUnit.parse("$d 12:00")
                }
            }
            time = if (date.timeInDays == now.timeInDays)
                now.timeInMills else date.timeInMills
            if (time == prevTime)
                time -= 30 * DateUnit.MIN_IN_MILLS
            prevTime = time
            row.put(Const.TIME, time)
            when {
                item.hasFewLinks() -> {
                    val sb = StringBuilder()
                    item.headsAndLinks().forEach {
                        sb.appendLine(it.second)
                        if (it.first.isEmpty())
                            sb.appendLine(getHeadFromUrl(it.second))
                        else sb.appendLine(it.first)
                    }
                    sb.delete(sb.length - 1, sb.length)
                    row.put(Const.LINK, sb.toString())
                }

                item.link.contains(":") ->
                    row.put(Const.LINK, item.link + Const.N + item.head)

                else -> //item.hasLink() or empty
                    row.put(Const.LINK, item.link)
            }
            storage.insert(row)
        }
        storage.close()
    }

    private fun loadList(link: String): List<BasicItem> {
        val page = PageParser(client)
        val isSite = link == Urls.Site
        val isCom = Urls.isSiteCom
        val end: String
        if (isSite) {
            page.load(link, "")
            end = if (isCom) "bgimage" else "<button"
        } else {
            end = if (isCom) "print2" else "<button"
            val i = link.lastIndexOf("/") + 1
            val url = link.substring(0, i) + Const.PRINT + link.substring(i)
            if (isCom) {
                page.load(url, "")
                page.nextItem
            } else page.load(url, "razdel")
        }
        var s: String? = page.currentElem
        var t: String
        var d = StringBuilder()
        val list = mutableListOf<BasicItem>()
        var item = BasicItem("")
        var prevLink = ""
        do {
            if (s != null) when {
                page.isHead -> {
                    if (isSite)
                        item = BasicItem(page.text, true)
                    else {
                        t = d.toString()
                        if (setDes(item, t).not()) list.add(BasicItem(t))
                        d = StringBuilder()
                        item = BasicItem(page.text)
                        addLink(item, "", "@")
                    }
                    list.add(item)
                }

                isSite -> {
                    if (s.contains(end)) break
                    if (page.link?.contains("javascript") == true) {
                        page.nextItem //DisableEnableUnread("true")
                        page.nextItem //DisableEnableUnread("false")
                        page.nextItem //непрочитанное
                    } else {
                        t = page.text
                        if (t.isNotEmpty() && !s.contains("\"#\"")) {
                            if (!s.contains("<")) item.des = s
                            else item = BasicItem(t).also { list.add(it) }
                            page.link?.let { addLink(item, t, it) }
                        }
                    }
                }

                page.isImage -> {
                    val p = parseImage(s)
                    if (p.second == prevLink) prevLink = ""
                    addLink(item, p.first, p.second)
                    d.append("<a href='" + p.second + "'>" + p.first + "</a><br>")
                }

                page.isSimple && s.indexOf("</") != 0 -> {
                    d.append(s)
                    if (prevLink.isNotEmpty()) {
                        addLink(item, s, prevLink)
                        prevLink = ""
                    }
                }

                else -> {
                    s = s.replace(" class='c0'", "").replace("  ", " ").replace("\"/print/", "\"")
                    page.link?.let {
                        prevLink = it.replace("/print", "")
                        if (page.text.isNotEmpty()) {
                            addLink(item, page.text, prevLink)
                            prevLink = ""
                        }
                    }
                    d.append(s)
                }
            }
            s = page.nextItem
        } while (s != null)
        t = d.toString()
        if (setDes(item, t).not())
            list.add(BasicItem(t))
        page.clear()
        if (isSite && isCom) {
            var i = list.size - 1
            while (i > 1) {
                if (i in 17..24 || i in 11..13 || (i in 2..6 && i != 4))
                    list.removeAt(i)
                i--
            }
        }
        return list
    }

    private fun parseImage(s: String): Pair<String, String> {
        val t = when {
            s.contains("title") -> "title"
            s.contains("alt") -> "alt"
            else -> "/"
        }
        var i = s.lastIndexOf(t) + if (t.length == 1) 1 else t.length + 2
        val title = s.substring(i, s.indexOf("\"", i))
        i = s.indexOf("src") + 5
        val url = s.substring(i, s.indexOf("\"", i))
        return Pair(title, url)
    }

    private fun addLink(item: BasicItem, head: String, link: String) {
        var url = link
        if (url.contains("files") || url.contains(".mp3") || url.contains(".wma"))
            url = Urls.Site + url.substring(1)
        if (url.startsWith("/")) url = url.substring(1)
        if (item.link == "@") item.clear()
        item.addLink(head, url)
    }

    private fun setDes(item: BasicItem?, d: String): Boolean {
        if (d.isEmpty())
            return true
        if (item == null)
            return false
        if (item.link == "#")
            return false
        item.des = d
        return true
    }

    private fun getHeadFromUrl(url: String) = when {
        url.contains("/") ->
            url.substring(url.lastIndexOf("/") + 1)

        url.contains(":") ->
            url.substring(url.lastIndexOf(":") + 1)

        else -> url
    }

    fun loadDevAds(): Boolean {
        if (time == -1L)
            time = storage.getTime()
        val t = time
        var hasNew = false
        val br = BufferedReader(InputStreamReader(client.getStream(Urls.DevAds)))
        val s = br.readLine()
        if (s.toLong() > t) {
            time = s.toLong()
            if (update(br)) {
                hasNew = true
                val unread = UnreadStorage()
                unread.setBadge(storage.unreadCount)
            }
        } else time = storage.newTime()
        br.close()
        return hasNew
    }

    private fun update(br: BufferedReader): Boolean {
        val titles = mutableListOf<String>()
        var mode: Int
        var index = 0
        var hasNew = false
        var item = SimpleItem("", "", "")
        br.forEachLine {
            when {
                it.contains("<e>") -> {
                    mode = when {
                        item.title.contains("<u>") -> DevStorage.TYPE_UPDATE
                        item.link.isEmpty() -> DevStorage.TYPE_DES
                        item.des.isEmpty() -> DevStorage.TYPE_LINK
                        else -> DevStorage.TYPE_ALL
                    }
                    if (item.title.contains("<w>")) warnIndex = index
                    index++
                    item.title = item.title.substring(3)
                    titles.add(item.title)
                    if (!storage.existsTitle(item.title)) {
                        hasNew = true
                        addRow(mode, item)
                    }
                    item = SimpleItem("", "", "")
                }

                it.indexOf("<") != 0 -> //multiline des
                    item.des += Const.N + it

                it.contains("<d>") ->
                    item.des = it.substring(3)

                it.contains("<l>") ->
                    item.link = it.substring(3)

                else -> item.title = it
            }
        }
        storage.deleteItems(titles)
        storage.newTime()
        return hasNew
    }

    private fun addRow(mode: Int, item: SimpleItem) {
        val row = ContentValues()
        row.put(Const.MODE, mode)
        row.put(Const.TITLE, item.title)
        row.put(Const.DESCRIPTION, item.des)
        row.put(Const.LINK, item.link)
        storage.insert(row)
    }
}