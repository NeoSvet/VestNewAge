package ru.neosvet.vestnewage.loader

import android.content.ContentValues
import org.json.JSONArray
import org.json.JSONObject
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.loader.basic.LinksProvider
import ru.neosvet.vestnewage.loader.basic.Loader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.UnreadUtils
import ru.neosvet.vestnewage.utils.fromHTML
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*

class CalendarLoader(private val client: NeoClient) : LinksProvider, Loader {
    private val storage = PageStorage()
    private var date = DateUnit.initToday()
    private val list = LinkedList<BasicItem>()
    private var isRun = false

    override fun load() {
        loadListMonth(true)
    }

    override fun cancel() {
        storage.close()
        isRun = false
    }

    fun setDate(year: Int, month: Int) {
        date = DateUnit.putYearMonth(year, month)
    }

    override fun getLinkList(): List<String> {
        storage.open(date.my)
        val list = storage.getLinksList()
        storage.close()
        return list
    }

    fun loadListMonth(updateUnread: Boolean) {
        isRun = true
        val url = Urls.getCalendar(date.month, date.year)
        val stream = client.getStream(url)
        val br = BufferedReader(InputStreamReader(stream), 1000)
        val s = br.readText()
        br.close()
        stream.close()
        if (s.length < 20) return
        if (Urls.isSiteCom)
            parseHtml(s)
        else
            parseJson(s)
        if (isRun) listToStorage(updateUnread)
    }

    private fun parseHtml(content: String) {
        val a = "href=\""
        var i = content.indexOf(a)
        while (i > -1) {
            i += a.length + 1
            val link = content.substring(i, content.indexOf("\"", i))
            i = content.indexOf(">", i) + 1
            val d = content.substring(i, content.indexOf("</a", i))
            if (d.contains("<"))
                list.add(BasicItem(d.fromHTML))
            else
                list.add(BasicItem(d))
            addLink(link)
            if (link.contains('_'))
                checkLink(link, '_')
            else if (link.contains('#'))
                checkLink(link, '#')
            if (date.year == 2016 && link.contains("2016")) {
                val add = when (date.month) {
                    1 -> false
                    2 -> !link.contains("05") && !link.contains("12")
                    4 -> !link.contains("06")
                    else -> true
                }
                if (add)
                    addLink(link.replace("2016", Const.POEMS))
            }
            i = content.indexOf(a, i)
        }
    }

    private fun checkLink(link: String, c: Char) {
        var n = link.indexOf(c) + 1
        n = link.substring(n, n + 1).toInt()
        if (n == 3)
            addLink(link.replace("$c$n", "${c}2"))
        addLink(link.replace("$c$n", ""))
    }

    private fun parseJson(content: String) {
        var json: JSONObject? = JSONObject(content)
        json = json?.getJSONObject("calendarData")
        if (json?.names() == null) return
        val names = json.names()!!
        var jsonI: JSONObject?
        var jsonA: JSONArray?
        var link: String
        var d: DateUnit
        var i = 0
        while (i < names.length() && isRun) {
            val s = names[i].toString()
            jsonI = json.optJSONObject(s)
            list.add(BasicItem(s.substring(s.lastIndexOf("-") + 1)))
            if (jsonI == null) { // массив за день (катрен и ещё какой-то текст (послание или статья)
                d = DateUnit.parse(s)
                jsonA = json.optJSONArray(s)
                if (jsonA == null) {
                    i++
                    continue
                }
                for (j in 0 until jsonA.length()) {
                    jsonI = jsonA.getJSONObject(j)
                    link = jsonI.getString(Const.LINK) + Const.HTML
                    if (link.contains(d.toString())) addLink(link)
                    else addLink("$d@$link")
                }
            } else { // один элемент за день (один или несколько катренов)
                link = jsonI.getString(Const.LINK) + Const.HTML
                addLink(link)
                jsonA = jsonI.getJSONObject("data").optJSONArray("titles")
                if (jsonA == null) {
                    i++
                    continue
                }
                for (j in 0 until jsonA.length())
                    addLink(link + "#" + (j + 2))
            }
            i++
        }
    }

    private fun listToStorage(updateUnread: Boolean) {
        storage.open(date.my)
        storage.updateTime()
        val unread = if (updateUnread) UnreadUtils() else null
        while (list.isNotEmpty()) {
            val item = if (Urls.isSiteCom)
                list.removeFirst() else list.removeLast()
            if (updateUnread)
                date.day = item.title.toInt()
            if (item.hasFewLinks()) {
                val links = mutableListOf<String>()
                item.links.forEach {
                    links.add(it)
                }
                links.sort()
                links.forEach { link ->
                    linkToStorage(link)
                    unread?.addLink(link, date)
                }
                links.clear()
            } else {
                linkToStorage(item.link)
                unread?.addLink(item.link, date)
            }
        }
        storage.close()
        unread?.setBadge()
    }

    private fun linkToStorage(link: String) {
        val row = ContentValues()
        row.put(Const.LINK, link)
        // пытаемся обновить запись:
        if (!storage.updateTitle(link, row)) {
            // обновить не получилось, добавляем:
            if (link.contains("@"))
                row.put(Const.TITLE, link.substring(9))
            else
                row.put(Const.TITLE, link)
            storage.insertTitle(row)
        }
    }

    private fun addLink(link: String) = list.last().let { item ->
        item.links.forEach {
            if (it == link) return@let
        }
        item.addLink(link)
    }
}