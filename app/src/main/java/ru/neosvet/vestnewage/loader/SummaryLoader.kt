package ru.neosvet.vestnewage.loader

import android.content.ContentValues
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.loader.basic.LinksProvider
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.NetConst
import ru.neosvet.vestnewage.storage.AdditionStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.UnreadUtils
import java.io.*

class SummaryLoader : LinksProvider {
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
        val stream: InputStream =
            NeoClient.getStream(NetConst.SITE + "rss/?" + System.currentTimeMillis())
        val site = if (NeoClient.isMainSite)
            NetConst.SITE.substring(
                NetConst.SITE.indexOf("/") + 2
            )
        else
            NetConst.SITE2.substring(
                NetConst.SITE2.indexOf("/") + 2
            )

        val br = BufferedReader(InputStreamReader(stream), 1000)
        val bw = BufferedWriter(FileWriter(Lib.getFile(Const.RSS)))
        val now = DateUnit.initNow()
        val unread = if (updateUnread) UnreadUtils() else null
        val m = br.readLine().split("<item>").toTypedArray()
        br.close()
        stream.close()
        var line: String
        var a: Int
        var b: Int
        for (i in 1 until m.size) {
            a = m[i].indexOf("</link")
            line = withOutTag(m[i].substring(0, a))
            if (line.contains(site)) line = line.substring(line.indexOf("info/") + 5)
            if (line.contains("#0")) line = line.replace("#0", "#2")
            b = m[i].indexOf("</title")
            bw.write(withOutTag(m[i].substring(a + 10, b))) //title
            bw.write(Const.N)
            bw.write(withOutTag(line)) //link
            bw.write(Const.N)
            unread?.addLink(line, now)
            a = m[i].indexOf("</des")
            bw.write(withOutTag(m[i].substring(b + 10, a))) //des
            bw.write(Const.N)
            b = m[i].indexOf("</a10")
            bw.write(
                DateUnit.parse(withOutTag(m[i].substring(a + 15, b)))
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

    fun loadAddition(storage: AdditionStorage, startId: Int) {
        if (maxPost == 0) {
            loadChanges(storage)
            maxPost = loadMax()
        }
        if (storage.max == 0) storage.findMax()
        var n = if (storage.max > 0) storage.max
        else maxPost - Const.MAX_ON_PAGE
        while (n < maxPost) {
            n++
            if (storage.hasPost(n).not())
                storage.insert(loadPost(n))
        }
        if (startId >= maxPost || startId == 0) return
        var end = startId - Const.MAX_ON_PAGE + 1
        if (end < 1) end = 1
        for (i in startId downTo end) {
            if (storage.hasPost(i).not())
                storage.insert(loadPost(i))
        }
    }

    private fun loadPost(id: Int): ContentValues {
        val d = (id / 200).toString()
        val stream = NeoClient.getStream(NetConst.ADDITION_URL + "$d/$id.p")
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

    private fun loadMax(): Int {
        val stream = NeoClient.getStream(NetConst.ADDITION_URL + "max.txt")
        val br = BufferedReader(InputStreamReader(stream, Const.ENCODING), 1000)
        val s = br.readLine()
        br.close()
        return s.toInt()
    }

    private fun loadChanges(storage: AdditionStorage) {
        val baseTime = Lib.getFileDB(DataBase.ADDITION).lastModified()
        val stream = NeoClient.getStream(NetConst.ADDITION_URL + "changed.txt")
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