package ru.neosvet.vestnewage.loader

import android.content.ContentValues
import org.json.JSONArray
import org.json.JSONObject
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.loader.basic.LinksProvider
import ru.neosvet.vestnewage.loader.basic.Loader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.NetConst
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.UnreadUtils
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.*

class CalendarLoader : LinksProvider, Loader {
    private var date = DateUnit.initToday()
    private val list = Stack<ListItem>()
    private var isRun = false

    override fun cancel() {
        isRun = false
        val stack = Stack<ListItem>()
        stack.iterator()
    }

    fun setDate(year: Int, month: Int) {
        date = DateUnit.putYearMonth(year, month)
    }

    override fun getLinkList(): List<String> {
        val storage = PageStorage()
        storage.open(date.my)
        val list = storage.getLinksList()
        storage.close()
        return list
    }

    fun loadListMonth(updateUnread: Boolean) {
        isRun = true
        val stream: InputStream = NeoClient.getStream(
            NetConst.SITE + "AjaxData/Calendar/"
                    + date.year + "-" + date.month + ".json"
        )
        val br = BufferedReader(InputStreamReader(stream), 1000)
        var s = br.readLine()
        br.close()
        stream.close()
        if (s.length < 20) return
        var json: JSONObject? = JSONObject(s)
        json = json!!.getJSONObject("calendarData")
        if (json?.names() == null) return
        var jsonI: JSONObject?
        var jsonA: JSONArray?
        var link: String
        var d: DateUnit
        var i = 0
        while (i < json.names().length() && isRun) {
            s = json.names()[i].toString()
            jsonI = json.optJSONObject(s)
            list.add(ListItem(s.substring(s.lastIndexOf("-") + 1)))
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
        if (isRun) listToStorage(updateUnread)
    }

    private fun listToStorage(updateUnread: Boolean) {
        val storage = PageStorage()
        storage.open(date.my)
        storage.updateTime()
        val unread = if (updateUnread) UnreadUtils() else null
        while (!list.empty()) {
            val item = list.pop()
            if (updateUnread)
                date.day = item.title.toInt()
            if (item.hasFewLinks()) {
                val links = mutableListOf<String>()
                item.links.forEach {
                    links.add(it)
                }
                links.sort()
                links.forEach { link ->
                    linkToStorage(storage, link)
                    unread?.addLink(link, date)
                }
                links.clear()
            } else {
                linkToStorage(storage, item.link)
                unread?.addLink(item.link, date)
            }
        }
        storage.close()
        unread?.setBadge()
    }

    private fun linkToStorage(storage: PageStorage, link: String) {
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

    private fun addLink(link: String) = list.peek()?.let { item ->
        item.links.forEach {
            if (it == link) return@let
        }
        item.addLink(link)
    }
}