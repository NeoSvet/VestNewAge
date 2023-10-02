package ru.neosvet.vestnewage.loader

import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.loader.basic.LinksProvider
import ru.neosvet.vestnewage.loader.page.PageParser
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.viewmodel.SiteToiler
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileOutputStream
import java.io.FileReader
import java.io.OutputStreamWriter
import java.util.regex.Pattern

class SiteLoader(
    private val client: NeoClient,
    private val file: String
) : LinksProvider {
    private val patternList: Pattern by lazy {
        Pattern.compile("\\d{4}\\.html")
    }

    override fun getLinkList(): List<String> {
        val list = mutableListOf<String>()
        if (file.contains(SiteToiler.NEWS))
            return list
        list.add(Urls.News)
        val br = BufferedReader(FileReader(file))
        br.forEachLine {
            if (isNeedLoad(it)) {
                if (it.contains("@"))
                    list.add(it.substring(9))
                else list.add(it)
            }
        }
        br.close()
        return list
    }

    private fun isNeedLoad(link: String): Boolean {
        if (!link.contains(Const.HTML))
            return false
        if (link.contains("tolkovaniya") || link.contains("/") && link.length < 18)
            return false
        return !patternList.matcher(link).matches()
    }

    fun load(url: String): List<BasicItem> {
        val list = loadList(url)
        saveList(list)
        return list
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
        do {
            when {
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
                    if (s == null || s.contains(end)) break
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

                page.isImage -> {}
                page.isSimple ->
                    d.append(s)

                else -> {
                    if (isCom && s != null)
                        s = s.replace(" class='c0'", "").replace("  ", " ")
                            .replace("\"/print/", "\"")
                    page.link?.let { addLink(item, page.text, it.replace("/print/", "")) }
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

    private fun addLink(item: BasicItem, head: String, link: String) {
        var url = link
        if (url.contains("files") || url.contains(".mp3") || url.contains(".wma"))
            url = Urls.Site + url.substring(1)
        if (url.indexOf("/") == 0) url = url.substring(1)
        if (item.link == "@")
            item.clear()
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

    private fun saveList(list: List<BasicItem>) {
        val bw = BufferedWriter(OutputStreamWriter(FileOutputStream(file)))
        for (i in list.indices) {
            bw.write(list[i].title + Const.N)
            bw.write(list[i].des + Const.N)
            when {
                list[i].hasFewLinks() -> {
                    list[i].headsAndLinks().forEach {
                        bw.write(it.second + Const.N)
                        bw.write(it.first + Const.N)
                    }
                }

                list[i].hasLink() -> bw.write(list[i].link + Const.N)
                else -> bw.write("@" + Const.N)
            }
            bw.write(SiteToiler.END + Const.N)
            bw.flush()
        }
        bw.close()
    }
}