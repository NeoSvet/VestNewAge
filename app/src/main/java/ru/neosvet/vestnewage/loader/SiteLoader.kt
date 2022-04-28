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
    private val patternList = Pattern.compile("\\d{4}\\.html")
    private val list = mutableListOf<ListItem>()

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
        list.clear()
        loadList(url)
        saveList()
        return list
    }

    private fun loadList(link: String) {
        val page = PageParser()
        val isSite = link == NeoClient.SITE
        var i: Int
        if (isSite) {
            page.load(link, "page-title")
        } else {
            i = link.lastIndexOf("/") + 1
            val url  = link.substring(0, i) + Const.PRINT + link.substring(i)
            page.load(url, "razdel")
        }
        var s: String? = page.firstElem ?: return
        var t: String
        var a: String?
        var d = StringBuilder()
        do {
            if (page.isHead) {
                if (isSite)
                    list.add(ListItem(page.text, true))
                else {
                    setDes(d.toString())
                    d = StringBuilder()
                    list.add(ListItem(page.text))
                    addLink("", "@")
                }
            } else {
                a = page.link
                t = page.text
                if (isSite) {
                    if (t.isNotEmpty() && !s!!.contains("\"#\"")) {
                        if (s.contains("&times;") || s.contains("<button>")) break
                        if (!s.contains("<")) list[list.size - 1].des = s
                        else list.add(ListItem(t))
                        a?.let { addLink(t, it) }
                    }
                } else {
                    if (t.isEmpty()) {
                        s = page.nextItem
                        if (s.contains("title=")) {
                            i = s.indexOf("title=") + 7
                            t = s.substring(i, s.indexOf("\"", i))
                        } else if (s.contains("alt=")) {
                            i = s.indexOf("alt=") + 5
                            t = s.substring(i, s.indexOf("\"", i))
                        }
                        s = "<a href='$a'>$t</a><br>"
                    }
                    a?.let { addLink(t, it) }
                    d.append(s)
                }
            }
            s = page.nextItem
            while (!page.curItem().start && s != null) s = page.nextItem
        } while (s != null)
        setDes(d.toString())
        page.clear()
    }

    private fun addLink(head: String, link: String) {
        var url = link
        if (url.contains("files") || url.contains(".mp3") || url.contains(".wma")
            || url.lastIndexOf("/") == url.length - 1)
                url = NeoClient.SITE + url.substring(1)
        if (url.indexOf("/") == 0) url = url.substring(1)
        list[list.size - 1].addLink(head, url)
    }

    private fun setDes(d: String) {
        if (list.size > 0) {
            if (d != "") {
                val i = list.size - 1
                if (list[i].link == "#") list.add(ListItem(d)) else list[i].des =
                    d
            }
        }
    }

    private fun saveList() {
        val bw = BufferedWriter(OutputStreamWriter(FileOutputStream(file)))
        var j: Int
        for (i in list.indices) {
            bw.write(list[i].title + Const.N)
            bw.write(list[i].des + Const.N)
            when (list[i].count) {
                0 -> bw.write("@" + Const.N)
                1 -> bw.write(list[i].link + Const.N)
                else -> {
                    j = 0
                    while (j < list[i].count) {
                        bw.write(list[i].getLink(j) + Const.N)
                        bw.write(list[i].getHead(j) + Const.N)
                        j++
                    }
                }
            }
            bw.write(SiteModel.END + Const.N)
            bw.flush()
        }
        bw.close()
    }
}