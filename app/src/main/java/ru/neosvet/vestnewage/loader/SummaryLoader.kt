package ru.neosvet.vestnewage.loader

import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.helper.SummaryHelper
import ru.neosvet.vestnewage.loader.basic.Loader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.UnreadUtils
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.InputStreamReader

class SummaryLoader(private val client: NeoClient) : Loader {
    var updateUnread = true

    override fun cancel() {}

    override fun load() {
        val stream = client.getStream(Urls.Rss)
        val host = Urls.Host
        val br = BufferedReader(InputStreamReader(stream), 1000)
        var s = br.readLine()
        if (Urls.isSiteCom) {
            while (!s.contains("pubDate"))
                s = br.readLine()
        }
        var a = s.indexOf("Date>") + 5
        val secList = DateUnit.parse(s.substring(a, s.indexOf("<", a))).timeInSeconds
        val file = Lib.getFile(Const.RSS)
        val secFile = if (file.exists())
            DateUnit.putMills(file.lastModified()).timeInSeconds
        else 0L
        val needUpdate = secFile < secList

        val bw = BufferedWriter(FileWriter(Lib.getFile(Const.RSS)))
        val now = DateUnit.initNow()
        val unread = if (updateUnread) UnreadUtils() else null
        val m = (if (Urls.isSiteCom) br.readText() else s).split("<item>")
        br.close()
        stream.close()
        var b: Int
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
        if (needUpdate) {
            val summaryHelper = SummaryHelper()
            summaryHelper.updateBook()
        }
    }

    private fun withOutTag(s: String): String {
        return s.substring(s.indexOf(">") + 1)
    }
}