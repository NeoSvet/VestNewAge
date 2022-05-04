package ru.neosvet.vestnewage.loader

import ru.neosvet.html.PageParser
import ru.neosvet.utils.Const
import ru.neosvet.utils.NeoClient
import ru.neosvet.vestnewage.list.ListItem
import ru.neosvet.vestnewage.loader.basic.LinksProvider
import ru.neosvet.vestnewage.model.SiteModel
import java.io.*
import java.util.regex.Pattern

class SiteLoader(private val file: String) : LinksProvider {
    private val patternList: Pattern by lazy {
        Pattern.compile("\\d{4}\\.html")
    }

    override fun getLinkList(): List<String> {
        val list = mutableListOf<String>()
        if (file.contains(SiteModel.NEWS))
            return list
        val br = BufferedReader(FileReader(file))
        var s: String? = br.readLine()
        while (s != null) {
            if (isNeedLoad(s)) {
                if (s.contains("@"))
                    list.add(s.substring(9))
                else list.add(s)
            }
            s = br.readLine()
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

    fun load(url: String): List<ListItem> {
        val list = loadList(url)
        saveList(list)
        return list
    }

    private fun loadList(link: String): List<ListItem> {
        val page = PageParser()
        val isSite = link == NeoClient.SITE
        if (isSite) {
            page.load(link, "page-title")
        } else {
            val i = link.lastIndexOf("/") + 1
            val url = link.substring(0, i) + Const.PRINT + link.substring(i)
            page.load(url, "razdel")
        }
        var s: String? = page.currentElem
        var t: String
        var a: String?
        var d = StringBuilder()
        val list = mutableListOf<ListItem>()
        var item: ListItem? = null
        do {
            if (page.isHead) {
                if (isSite)
                    item = ListItem(page.text, true)
                else {
                    t = d.toString()
                    if (setDes(item, t).not())
                        list.add(ListItem(t))
                    d = StringBuilder()
                    item = ListItem(page.text)
                    addLink(item, "", "@")
                }
                list.add(item)
            } else {
                a = page.link
                t = page.text
                if (isSite) {
                    if (t.isNotEmpty() && !s!!.contains("\"#\"")) {
                        if (s.contains("&times;") || s.contains("<button>")) break
                        if (!s.contains("<")) item?.des = s
                        else {
                            item = ListItem(t)
                            list.add(item)
                        }
                        a?.let { addLink(item!!, t, it) }
                    }
                } else {
                    if (t.isEmpty()) {
                        s = page.nextItem
                        t = getTitleItem(s)
                        s = "<a href='$a'>$t</a><br>"
                    }
                    a?.let { addLink(item!!, t, it) }
                    d.append(s)
                }
            }
            s = page.nextItem
            while (!page.curItem().start && s != null)
                s = page.nextItem
        } while (s != null)
        t = d.toString()
        if (setDes(item, t).not())
            list.add(ListItem(t))
        page.clear()
        return list
    }

    private fun getTitleItem(s: String): String {
        val i = when {
            s.contains("title=") ->
                s.indexOf("title=") + 7
            s.contains("alt=") ->
                s.indexOf("alt=") + 5
            else -> return ""
        }
        return s.substring(i, s.indexOf("\"", i))
    }

    private fun addLink(item: ListItem, head: String, link: String) {
        var url = link
        if (url.contains("files") || url.contains(".mp3") || url.contains(".wma")
            || url.lastIndexOf("/") == url.length - 1
        )
            url = NeoClient.SITE + url.substring(1)
        if (url.indexOf("/") == 0) url = url.substring(1)
        if (item.link == "@")
            item.clear()
        item.addLink(head, url)
    }

    private fun setDes(item: ListItem?, d: String): Boolean {
        if (d.isEmpty())
            return true
        if (item == null)
            return false
        if (item.link == "#")
            return false
        item.des = d
        return true
    }

    private fun saveList(list: List<ListItem>) {
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
            bw.write(SiteModel.END + Const.N)
            bw.flush()
        }
        bw.close()
    }
}