package ru.neosvet.vestnewage.loader

import android.content.ContentValues
import org.json.JSONArray
import org.json.JSONObject
import ru.neosvet.utils.Const
import ru.neosvet.utils.NeoClient
import ru.neosvet.vestnewage.helpers.DateHelper
import ru.neosvet.vestnewage.helpers.LoaderHelper
import ru.neosvet.vestnewage.helpers.ProgressHelper
import ru.neosvet.vestnewage.helpers.UnreadHelper
import ru.neosvet.vestnewage.list.ListItem
import ru.neosvet.vestnewage.storage.PageStorage
import java.io.*

class CalendarLoader : ListLoader {
    private var date = DateHelper.initToday()
    private val storage = PageStorage()
    private val list: MutableList<ListItem> by lazy {
        mutableListOf()
    }

    fun setDate(year: Int, month: Int) {
        date = DateHelper.putYearMonth(year, month)
    }

    override fun getLinkList(): Int {
        storage.open(date.my)
        val curTitle = storage.getLinks()
        var k = 0
        if (curTitle.moveToFirst()) {
            // пропускаем первую запись - там только дата изменения списка
            var link: String?
            val file = LoaderHelper.getFileList()
            val bw = BufferedWriter(FileWriter(file))
            while (curTitle.moveToNext()) {
                link = curTitle.getString(0)
                bw.write(link)
                k++
                bw.newLine()
                bw.flush()
            }
            bw.close()
        }
        curTitle.close()
        storage.close()
        return k
    }

    fun loadListMonth(updateUnread: Boolean) {
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
        while (i < json.names().length() && !ProgressHelper.isCancelled()) {
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
        if (ProgressHelper.isCancelled()) {
            list.clear()
            return
        }
        if (updateUnread) {
            val unread = UnreadHelper()
            for (i in list.indices) {
                for (j in 0 until list[i].count) {
                    date.day = list[i].title.toInt()
                    unread.addLink(list[i].getLink(j), date)
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
        if (!storage.updateTitle(1, row)) storage.insertTitle(row)
    }

    fun loadListYear(year: Int, max_m: Int) {
        var m = 1
        while (m < max_m && LoaderHelper.start) {
            setDate(year, m)
            loadListMonth(false)
            ProgressHelper.upProg()
            m++
        }
    }

    private fun addLink(n: Int, link: String) {
        if (list[n].count > 0) {
            for (i in 0 until list[n].count) {
                if (list[n].getLink(i).contains(link)) return
            }
        }
        list[n].addLink(link)
        val row = ContentValues()
        row.put(Const.LINK, link)
        // пытаемся обновить запись:
        if (!storage.updateTitle(link, row)) {
            // обновить не получилось, добавляем:
            if (link.contains("@")) row.put(
                Const.TITLE,
                link.substring(9)
            ) else row.put(Const.TITLE, link)
            storage.insertTitle(row)
        }
    }
}