package ru.neosvet.vestnewage.loader

import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.NeoList
import ru.neosvet.vestnewage.loader.basic.Loader
import ru.neosvet.vestnewage.loader.page.PageLoader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.UnreadStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Files
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileReader
import java.io.FileWriter
import java.io.InputStreamReader

class UpdateLoader(private val client: NeoClient) : Loader {
    private var isRun = false

    override fun load() {
        isRun = true
    }

    override fun cancel() {
        isRun = false
    }

    fun checkAcademy(): Boolean {
        val file = Files.file(Files.ACADEMY)
        if (file.exists()) {
            if (!DateUnit.isVeryLongAgo(file.lastModified()))
                 return false
            var br = BufferedReader(FileReader(file))
            br.readLine() //title
            br.readLine() //link
            br.readLine() //des always empty
            val timeFile = br.readLine()?.toLong() ?: 0
            br.close()

            load()
            val stream = client.getStream(Urls.ACADEMY + "/Press/News/")
            br = BufferedReader(InputStreamReader(stream), 1000)
            var s = br.readLine()
            while (!s.contains("sm-blog-list-date"))
                s = br.readLine()
            br.close()
            val i = s.indexOf("<span>") + 6
            s = s.substring(i, s.indexOf("<", i))
            val timeList = DateUnit.parse(s).timeInMills

            if (timeFile == timeList) {
                file.setLastModified(System.currentTimeMillis())
                return false
            }
        }

        if(!isRun) return true
        val loader = SummaryLoader(client)
        loader.loadAcademy()
        return true
    }

    fun checkDoctrine(): Boolean {
        val file = Files.file(Files.DOCTRINE)
        if (file.exists()) {
            if (!DateUnit.isVeryLongAgo(file.lastModified()))
                return false
            var br = BufferedReader(FileReader(file))
            var s = br.readLine()
            while (!s.contains(Const.END)) //end of des
                s = br.readLine()
            val timeFile = br.readLine().toLong()
            br.close()

            load()
            val stream = client.getStream(Urls.DOCTRINE + "feed/")
            br = BufferedReader(InputStreamReader(stream), 1000)
            s = br.readLine()
            while (!s.contains("pubDate"))
                s = br.readLine()
            val i = s.indexOf("Date>") + 5
            br.close()
            val timeList = DateUnit.parse(s.substring(i, s.indexOf("<", i))).timeInMills

            if (timeFile == timeList) {
                file.setLastModified(System.currentTimeMillis())
                return false
            }
        }

        if(!isRun) return true
        val loader = SummaryLoader(client)
        loader.loadDoctrine()
        return true
    }

    fun checkAddition(): Boolean {
        val loader = AdditionLoader(client)
        return loader.checkUpdate()
    }

    fun checkSummary(updateUnread: Boolean): NeoList<Pair<String, String>> {
        val list = NeoList<Pair<String, String>>()
        load()
        val stream = client.getStream(Urls.RSS)
        val br = BufferedReader(InputStreamReader(stream), 1000)
        var s = br.readLine()
        if (Urls.isSiteCom) {
            while (!s.contains("pubDate"))
                s = br.readLine()
        }
        var a = s.indexOf("Date>") + 5
        val timeList = DateUnit.parse(s.substring(a, s.indexOf("<", a))).timeInMills
        val file = Files.file(Files.RSS)
        val timeFile = if (file.exists()) file.lastModified()
        else 0L
        if (timeFile > timeList) {
            br.close()
            file.setLastModified(System.currentTimeMillis())
            return list
        }
        val m = (if (Urls.isSiteCom) br.readText() else s).split("<item>")
        br.close()
        val bw = BufferedWriter(FileWriter(file))
        val host = Urls.Host
        val unread = if (updateUnread) UnreadStorage() else null
        var d: DateUnit
        var title: String
        var link: String
        var b: Int
        val loader = PageLoader(client)

        for (i in 1 until m.size) {
            a = m[i].indexOf("<link") + 6
            b = m[i].indexOf("</", a)
            link = m[i].substring(a, b)
            if (link.contains(host))
                link = link.substring(link.indexOf(host) + host.length + 1)
            if (link.contains("#0")) link = link.replace("#0", "#2")

            a = m[i].indexOf("<title") + 7
            b = m[i].indexOf("</", a)
            title = m[i].substring(a, b)
            bw.write(title) //title
            bw.write(Const.N)
            bw.write(link) //link
            bw.write(Const.N)

            a = m[i].indexOf("<des") + 13
            b = m[i].indexOf("</", a)
            bw.write(withOutTag(m[i].substring(a, b))) //des
            bw.write(Const.N)

            a = m[i].indexOf("updated>")
            if (a == -1)
                a = m[i].indexOf("pubDate>")
            b = m[i].indexOf("</", a)
            d = DateUnit.parse(m[i].substring(a + 8, b))
            bw.write(d.timeInMills.toString() + Const.N) //time
            bw.flush()

            if (unread?.addLink(link, d) == true) {
                list.add(Pair(title, link))
                loader.download(link, false)
            }
            if(!isRun) break
        }
        bw.close()
        loader.finish()
        unread?.setBadge()
        unread?.close()
        return list
    }

    fun checkCalendar(): String? {
        val loader = CalendarLoader(client)
        loader.load()
        val list = loader.getLinkList()
        val link = if(list.isEmpty()) "" else list.last()
        val today = DateUnit.initMskNow().toShortDateString()
        if (link.contains(today))
            return link
        return null
    }

    private fun withOutTag(s: String): String {
        return s.substring(s.indexOf(">") + 1)
    }
}