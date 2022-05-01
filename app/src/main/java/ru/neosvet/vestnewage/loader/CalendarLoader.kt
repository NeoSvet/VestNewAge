package ru.neosvet.vestnewage.loader

import android.content.ContentValues
import org.json.JSONArray
import org.json.JSONObject
import ru.neosvet.utils.Const
import ru.neosvet.utils.NeoClient
import ru.neosvet.vestnewage.helpers.DateHelper
import ru.neosvet.vestnewage.helpers.UnreadHelper
import ru.neosvet.vestnewage.list.ListItem
import ru.neosvet.vestnewage.loader.basic.LinksProvider
import ru.neosvet.vestnewage.loader.basic.Loader
import ru.neosvet.vestnewage.storage.PageStorage
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

class CalendarLoader : LinksProvider, Loader {
    private var date = DateHelper.initToday()
    private val storage = PageStorage()
    private val list: MutableList<ListItem> by lazy {
        mutableListOf()
    }
    private var isRun = false
    val curDate: DateHelper
        get() = date

    override fun cancel() {
        isRun = false
    }

    fun setDate(year: Int, month: Int) {
        date = DateHelper.putYearMonth(year, month)
    }

    override fun getLinkList(): List<String> {
        storage.open(date.my)
        val list = mutableListOf<String>()
        val cursor = storage.getLinks()
        if (cursor.moveToFirst()) {
            // пропускаем первую запись - там только дата изменения списка
            while (cursor.moveToNext()) {
                val link = cursor.getString(0)
                list.add(link)
            }
        }
        cursor.close()
        storage.close()
        return list
    }

    fun loadListMonth(updateUnread: Boolean) {
        isRun = true
        val stream: InputStream = NeoClient.getStream(
            NeoClient.SITE + "AjaxData/Calendar/"
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
        initDatabase(date.my)
        var jsonI: JSONObject?
        var jsonA: JSONArray?
        var link: String
        var d: DateHelper
        var n: Int
        var i = 0
        while (i < json.names().length() && isRun) {
            s = json.names()[i].toString()
            jsonI = json.optJSONObject(s)
            n = list.size
            list.add(ListItem(s.substring(s.lastIndexOf("-") + 1)))
            if (jsonI == null) { // массив за день (катрен и ещё какой-то текст (послание или статья)
                d = DateHelper.parse(s)
                jsonA = json.optJSONArray(s)
                if (jsonA == null) {
                    i++
                    continue
                }
                for (j in 0 until jsonA.length()) {
                    jsonI = jsonA.getJSONObject(j)
                    link = jsonI.getString(Const.LINK) + Const.HTML
                    if (link.contains(d.toString())) addLink(n, link)
                    else addLink(n, "$d@$link")
                }
            } else { // один элемент за день (один или несколько катренов)
                link = jsonI.getString(Const.LINK) + Const.HTML
                addLink(n, link)
                jsonA = jsonI.getJSONObject("data").optJSONArray("titles")
                if (jsonA == null) {
                    i++
                    continue
                }
                for (j in 0 until jsonA.length())
                    addLink(n, link + "#" + (j + 2))
            }
            i++
        }
        storage.close()
        if (isRun.not()) {
            list.clear()
            return
        }
        if (updateUnread) {
            val unread = UnreadHelper()
            for (x in list.indices) {
                date.day = list[x].title.toInt()
                list[x].links.forEach {
                    unread.addLink(it, date)
                }
            }
            unread.setBadge()
        }
        list.clear()
    }

    private fun initDatabase(name: String) {
        storage.open(name)
        val row = ContentValues()
        row.put(Const.TIME, System.currentTimeMillis())
        if (!storage.updateTitle(1, row))
            storage.insertTitle(row)
    }

    fun loadListYear(year: Int, max_m: Int) {
        isRun = true
        var m = 1
        while (m < max_m && isRun) {
            setDate(year, m)
            loadListMonth(false)
            m++
        }
    }

    private fun addLink(n: Int, link: String) {
        list[n].links.forEach {
            if (it.contains(link)) return
        }
        list[n].addLink(link)
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
}