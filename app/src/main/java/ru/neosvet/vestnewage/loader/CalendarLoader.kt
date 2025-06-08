package ru.neosvet.vestnewage.loader

import org.json.JSONArray
import org.json.JSONObject
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.loader.basic.LinksProvider
import ru.neosvet.vestnewage.loader.basic.Loader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.storage.UnreadStorage
import ru.neosvet.vestnewage.utils.Const
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.LinkedList

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
        parseJson(s)
        if (isRun) listToStorage(updateUnread)
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
        val unread = if (updateUnread) UnreadStorage() else null
        while (list.isNotEmpty()) {
            val item = list.removeLast()
            if (updateUnread)
                date.day = item.title.toInt()
            if (item.hasFewLinks()) {
                val links = mutableListOf<String>()
                item.links.forEach {
                    links.add(it)
                }
                links.sort()
                links.forEach { link ->
                    storage.putLink(link)
                    unread?.addLink(link, date)
                }
                links.clear()
            } else {
                storage.putLink(item.link)
                unread?.addLink(item.link, date)
            }
        }
        storage.close()
        unread?.setBadge()
    }

    private fun addLink(link: String) = list.last().let { item ->
        item.links.forEach {
            if (it == link) return@let
        }
        item.addLink(link)
    }
}