package ru.neosvet.vestnewage.loader

import android.content.ContentValues
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.loader.basic.LinksProvider
import ru.neosvet.vestnewage.loader.basic.LoadHandlerLite
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.NetConst
import ru.neosvet.vestnewage.storage.AdditionStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.UnreadUtils
import ru.neosvet.vestnewage.utils.percent
import ru.neosvet.vestnewage.view.list.paging.NeoPaging
import java.io.*

class SummaryLoader(private val client: NeoClient) : LinksProvider {
    private var maxPost = 0

    override fun getLinkList(): List<String> {
        val br = BufferedReader(FileReader(App.context.filesDir.toString() + Const.RSS))
        val list = mutableListOf<String>()
        while (br.readLine() != null) { //title
            val link = br.readLine() //link
            list.add(link)
            br.readLine() //des
            br.readLine() //time
        }
        br.close()
        return list
    }

    fun loadRss(updateUnread: Boolean) {
        val stream = client.getStream(NetConst.SITE + "rss/?" + System.currentTimeMillis())
        val site = NeoClient.getSite()
        val br = BufferedReader(InputStreamReader(stream), 1000)
        val bw = BufferedWriter(FileWriter(Lib.getFile(Const.RSS)))
        val now = DateUnit.initNow()
        val unread = if (updateUnread) UnreadUtils() else null
        val m = (if (NeoClient.isSiteCom) {
            val sb = StringBuilder()
            var line: String? = br.readLine()
            while (line != null) {
                sb.append(line)
                line = br.readLine()
            }
            sb.toString()
        } else br.readLine())
            .split("<item>")
        br.close()
        stream.close()
        var a: Int
        var b: Int
        var s: String
        for (i in 1 until m.size) {
            a = m[i].indexOf("<link") + 6
            b = m[i].indexOf("</", a)
            s = m[i].substring(a, b)
            if (s.contains(site))
                s = s.substring(s.indexOf(site) + site.length + 1)
            if (s.contains("#0")) s = s.replace("#0", "#2")

            a = m[i].indexOf("<title") + 7
            b = m[i].indexOf("</", a)
            bw.write(m[i].substring(a, b)) //title
            bw.write(Const.N)
            bw.write(s) //link
            bw.write(Const.N)
            unread?.addLink(s, now)

            a = m[i].indexOf("<des") + 13
            b = m[i].indexOf("</", a)
            bw.write(withOutTag(m[i].substring(a, b))) //des
            bw.write(Const.N)

            a = m[i].indexOf("updated>")
            if (a == -1)
                a = m[i].indexOf("pubDate>")
            b = m[i].indexOf("</", a)
            bw.write(
                DateUnit.parse(m[i].substring(a + 8, b))
                    .timeInMills.toString() + Const.N
            ) //time

            bw.flush()
        }
        bw.close()
        unread?.setBadge()
    }

    private fun withOutTag(s: String): String {
        return s.substring(s.indexOf(">") + 1)
    }

    fun loadAllAddition(handler: LoadHandlerLite) {
        val storage = AdditionStorage()
        storage.open()
        if (maxPost == 0) {
            loadChanges(storage)
            maxPost = loadMax()
        }
        var p = maxPost
        while (p > 0) {
            if (storage.hasPost(p).not())
                storage.insert(loadPost(p))
            p--
            handler.postPercent(100 - p.percent(maxPost))
        }
        storage.close()
    }

    fun loadAddition(storage: AdditionStorage, startId: Int) {
        if (maxPost == 0) {
            loadChanges(storage)
            maxPost = loadMax()
        }
        if (storage.max == 0) storage.findMax()
        var n = if (storage.max > 0) storage.max
        else maxPost - NeoPaging.ON_PAGE
        while (n < maxPost) {
            n++
            if (storage.hasPost(n).not())
                storage.insert(loadPost(n))
        }
        if (startId >= maxPost || startId == 0) return
        var end = startId - NeoPaging.ON_PAGE + 1
        if (end < 1) end = 1
        for (i in startId downTo end) {
            if (storage.hasPost(i).not())
                storage.insert(loadPost(i))
        }
    }

    private val additionUrl: String
        get() = if (NeoClient.isSiteCom) NetConst.ADDITION_URL_COM else NetConst.ADDITION_URL

    private fun loadPost(id: Int): ContentValues {
        val d = (id / 200).toString()
        val stream = client.getStream("$additionUrl$d/$id.txt")
        val br = BufferedReader(InputStreamReader(stream, Const.ENCODING), 1000)
        val row = ContentValues()
        row.put(DataBase.ID, id)
        row.put(Const.TITLE, br.readLine())
        row.put(Const.LINK, br.readLine().toInt())
        var s: String? = br.readLine()
        val des = StringBuilder()
        while (s != null) {
            des.append(s)
            des.append(Const.N)
            s = br.readLine()
        }
        br.close()
        row.put(Const.DESCTRIPTION, des.toString().trim())
        return row
    }

    fun loadMax(): Int {
        val stream = client.getStream("${additionUrl}max.txt")
        val br = BufferedReader(InputStreamReader(stream, Const.ENCODING), 1000)
        val s = br.readLine()
        br.close()
        return s.toInt()
    }

    private fun loadChanges(storage: AdditionStorage) {
        val baseTime = Lib.getFileDB(DataBase.ADDITION).lastModified()
        val stream = client.getStream("${additionUrl}changed.txt")
        val br = BufferedReader(InputStreamReader(stream, Const.ENCODING), 1000)
        var s: String? = br.readLine()
        while (s != null) {
            val i = s.lastIndexOf(" ")
            if (s.substring(i + 1).toLong() > baseTime) {
                val id = s.substring(s.indexOf(" ") + 1, i).toInt()
                if (s.contains("delete"))
                    storage.delete(id)
                else if (s.contains("update")) {
                    val post = loadPost(id)
                    if (storage.update(id, post).not())
                        storage.insert(post)
                }
            }
            s = br.readLine()
        }
        br.close()
    }
}