package ru.neosvet.vestnewage.loader

import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.SimpleItem
import ru.neosvet.vestnewage.helper.SummaryHelper
import ru.neosvet.vestnewage.loader.basic.Loader
import ru.neosvet.vestnewage.loader.page.PageParser
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Files
import ru.neosvet.vestnewage.storage.UnreadStorage
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.InputStreamReader

class SummaryLoader(private val client: NeoClient) : Loader {

    override fun cancel() {}

    override fun load() {
        val stream = client.getStream(Urls.RSS)
        val host = Urls.Host
        val br = BufferedReader(InputStreamReader(stream), 1000)
        var s = br.readLine()
        if (Urls.isSiteCom) {
            while (!s.contains("pubDate"))
                s = br.readLine()
        }
        var a = s.indexOf("Date>") + 5
        val secList = DateUnit.parse(s.substring(a, s.indexOf("<", a))).timeInSeconds
        val file = Files.file(Files.RSS)
        val secFile = if (file.exists())
            DateUnit.putMills(file.lastModified()).timeInSeconds
        else 0L
        val needUpdate = secFile < secList

        val bw = BufferedWriter(FileWriter(file))
        val now = DateUnit.initNow()
        val unread = UnreadStorage()
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
            unread.addLink(s, now)

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
        unread.setBadge()
        unread.close()
        if (needUpdate) {
            val summaryHelper = SummaryHelper()
            summaryHelper.updateBook()
        }
    }

    private fun withOutTag(s: String): String {
        return s.substring(s.indexOf(">") + 1)
    }

    fun loadDoctrine() {
        val stream = client.getStream(Urls.DOCTRINE + "feed/")
        val br = BufferedReader(InputStreamReader(stream), 1000)
        val file = Files.file(Files.DOCTRINE)
        val bw = BufferedWriter(FileWriter(file))
        val m = br.readText().split("<item>")
        br.close()
        stream.close()
        val storage = PageStorage()
        storage.open(Const.DOCTRINE)
        var a: Int
        var b: Int
        var time: Long
        for (i in 1 until m.size - 1) {
            a = m[i].indexOf("<title") + 7
            b = m[i].indexOf("</", a)
            val title = m[i].substring(a, b)
            a = m[i].indexOf("<link", b) + 6
            b = m[i].indexOf("</", a)
            val link = m[i].substring(a, b).replace(Urls.DOCTRINE, Const.DOCTRINE)
            bw.write(title) //title
            bw.write(Const.N)
            bw.write(link) //link
            bw.write(Const.N)
            storage.putTitle(title, link)

            a = m[i].indexOf("pubDate>", b)
            b = m[i].indexOf("</", a)
            time = DateUnit.parse(m[i].substring(a + 8, b)).timeInMills
            storage.updateTime(link, time)

            a = m[i].indexOf("<des", b) + 22
            b = m[i].indexOf("]]><", a)
            bw.write(m[i].substring(a, b)) //des
            bw.write(Const.N)
            bw.write(Const.END)
            bw.write(Const.N)

            bw.write(time.toString() + Const.N) //time
            bw.flush()

            a = m[i].indexOf("<content", b) + 26
            b = m[i].indexOf("]]><", a)
            addContent(
                storage, storage.getPageId(link),
                m[i].substring(a, b)
            )
        }
        bw.close()
    }

    private fun addContent(storage: PageStorage, id: Int, content: String) {
        storage.deleteParagraphs(id)
        content.split("<p>").forEach {
            storage.insertParagraph(id, "<p>$it")
        }
    }

    fun loadAcademy() {
        val list = loadAcademyList("")
        val file = Files.file(Files.ACADEMY)
        val bw = BufferedWriter(FileWriter(file))
        var time: Long
        list.forEach {
            bw.write(it.title) //title
            bw.write(Const.N)
            bw.write(Urls.ACADEMY + it.link) //link
            bw.write(Const.N)
            bw.write(Const.N) //des
            time = DateUnit.parse(it.des).timeInMills
            bw.write(time.toString()) //time
            bw.write(Const.N)
            bw.flush()
        }
        bw.close()
    }

    private fun loadAcademyList(pageUrl: String): List<SimpleItem> {
        val page = PageParser(client)
        page.load(Urls.ACADEMY + "/Press/News/" + pageUrl, "row sm-razdel-listnews")
        var s: String? = page.nextItem
        var date: String
        val list = mutableListOf<SimpleItem>()
        var item = SimpleItem("", "", "")
        var p = -1
        val end = if (pageUrl.isEmpty()) "</nav>" else "<nav"
        do {
            if (page.isHead) s = page.nextItem
            page.link?.let {
                if (it != item.link) {
                    item.link = it
                    if (it.contains("pageIndex")) {
                        p++
                        if (p > 1)
                            list.addAll(loadAcademyList(it))
                    } else item.title = page.text
                }
            }
            s?.let {
                if (page.isSimple) {
                    date = it.trim()
                    if (date.length == 10) {
                        item.des = date
                        list.add(item)
                        item = SimpleItem("", "", "")
                    }
                }
            }
            s = page.nextItem
        } while (s?.indexOf(end) != 0)
        page.clear()
        return list
    }
}