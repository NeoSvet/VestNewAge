package ru.neosvet.vestnewage.loader

import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.loader.basic.LinksProvider
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.UnreadUtils
import java.io.*

class SummaryLoader : LinksProvider {
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

    fun loadList(addUnread: Boolean) {
        val stream: InputStream =
            NeoClient.getStream(NeoClient.SITE + "rss/?" + System.currentTimeMillis())
        val site = if (NeoClient.isMainSite())
            NeoClient.SITE.substring(
                NeoClient.SITE.indexOf("/") + 2
            )
        else
            NeoClient.SITE2.substring(
                NeoClient.SITE2.indexOf("/") + 2
            )

        val br = BufferedReader(InputStreamReader(stream), 1000)
        val bw = BufferedWriter(FileWriter(Lib.getFile(Const.RSS)))
        val now = DateUnit.initNow()
        val unread: UnreadUtils? = if (addUnread) UnreadUtils() else null
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
}