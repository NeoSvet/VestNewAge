package ru.neosvet.vestnewage.loader

import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.loader.basic.LinksProvider
import ru.neosvet.vestnewage.loader.basic.Loader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.UnreadUtils
import java.io.*

class SummaryLoader(private val client: NeoClient) : LinksProvider, Loader {
    var updateUnread = true

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

    override fun cancel() {}

    override fun load() {
        val stream = client.getStream(Urls.Rss)
        val host = Urls.Host
        val br = BufferedReader(InputStreamReader(stream), 1000)
        val bw = BufferedWriter(FileWriter(Lib.getFile(Const.RSS)))
        val now = DateUnit.initNow()
        val unread = if (updateUnread) UnreadUtils() else null
        val m = (if (Urls.isSiteCom) {
            val sb = StringBuilder()
            br.forEachLine {
                sb.append(it)
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
            if (s.contains(host))
                s = s.substring(s.indexOf(host) + host.length + 1)
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
}