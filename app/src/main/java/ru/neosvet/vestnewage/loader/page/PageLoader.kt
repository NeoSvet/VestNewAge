package ru.neosvet.vestnewage.loader.page

import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.loader.BookLoader
import ru.neosvet.vestnewage.loader.basic.Loader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.fromHTML
import java.io.BufferedReader
import java.io.InputStreamReader

class PageLoader(private val client: NeoClient) : Loader {
    private var kRequests = 0
    private var timeRequests: Long = 0
    private val storage = PageStorage()
    var isFinish = true
        private set

    override fun load() {
    }

    override fun cancel() {
        finish()
    }

    fun download(link: String, singlePage: Boolean): Boolean {
        isFinish = false
        // если singlePage=true, значит страницу страницу перезагружаем, а счетчики обрабатываем
        storage.open(link, true)
        val exists = storage.existsPage(link)
        if (!singlePage && exists)
            return false
        if (storage.isOtherBook) {
            downloadOtherPage(link)
            if (singlePage) storage.close()
            return true
        }
        if (!singlePage) checkRequests()
        val page = PageParser(client)
        page.load(Urls.Page + link, "")
        if (singlePage) storage.deleteParagraphs(storage.getPageId(link))
        val fix = link.contains("02.02.20") || link.contains("16.01.19") ||
                link.contains("08.10.18")

        var par = ""
        var hasNoind = false
        var s: String? = page.currentElem
        val time = System.currentTimeMillis()
        var id = link.indexOf("#")
        val adr = if (id > 0) {
            par = link.substring(id)
            link.substring(0, id)
        } else link
        id = 0
        do {
            page.link?.let {
                if (it.startsWith("name:")) {
                    par = "#" + it.substring(5)
                    s = page.nextElem
                }
            }
            if (page.isHead) {
                if (fix && s?.contains("02.02.20") == true)
                    s = s?.replace("20.20", "2020")
                hasNoind = false
                s = getTitle(s, storage.name)
                if (id > 0) storage.insertParagraph(
                    id, "<p class='noind'>" + App.context.getString(R.string.on_same_day) +
                            "<br><a href='${adr + par}'>${s?.replace("“", "")}</a></p>"
                )
                id = storage.putTitle(s!!, adr + par, time)
                if (exists) storage.deleteParagraphs(id)
                s = page.nextElem
            }
            s?.let {
                var e = if (it.contains("<br>"))
                    it.replace(" <br>", "<br>").replace("<br> ", "<br>")
                else it
                if (!hasNoind || e.contains("noind")) {
                    if (e.fromHTML.isNotEmpty()) {
                        if (e.contains("Аминь") && !e.contains("noind")) {
                            e = "<p class='noind'>" + e.substring(e.indexOf('>') + 1)
                            page.nextElem?.let {
                                e = e.substring(0, e.lastIndexOf("</"))
                                e += "<br>" + it.substring(it.indexOf('>') + 1)
                            }
                        }
                        storage.insertParagraph(id, e)
                    }
                }
                if (fix && hasNoind) {
                    hasNoind = false
                    s = if (e.startsWith("<p><br>")) {
                        //<p><br>Дополнение к Катрену через 5 часов от Создателя:</p> (16.01.19)
                        "<p class='noind'><strong" + e.substring(6, e.length - 2) + "strong></p>"
                    } else if (e.contains(":<"))//<p>Дополнение:</p> (08.10.18)
                        "<p class='noind'" + e.substring(2)
                    else null
                    s?.let { storage.insertParagraph(id, it) }
                }
                if (fix && e.contains("noind"))
                    hasNoind = true //for ignore Дополнение Информации от Создателя (02.02.20)
            }
            s = page.nextElem
        } while (s != null)
        if (singlePage) finish()
        page.clear()
        return true
    }

    private fun downloadOtherPage(link: String) {
        val isRus = link.contains(Const.HOLY_RUS)
        var id = storage.getPageId(link)
        if (id == -1) {
            val loader = BookLoader(client)
            loader.loadBookList(isRus)
            id = storage.getPageId(link)
        } else storage.deleteParagraphs(id)

        val m = if (isRus) arrayOf(Urls.HolyRusBase, Const.HOLY_RUS)
        else arrayOf(Urls.DoctrineBase, Const.DOCTRINE)
        var s: String? = link.substring(m[1].length) //pages
        val stream = client.getStream("${m[0]}$s.txt")
        val br = BufferedReader(InputStreamReader(stream, Const.ENCODING), 1000)
        storage.updateTime(link, br.readLine().toLong())
        s = br.readLine()
        while (s != null) {
            storage.insertParagraph(id, s)
            s = br.readLine()
        }
        br.close()
    }

    private fun checkRequests() {
        kRequests++
        if (kRequests == 5) {
            val now = System.currentTimeMillis()
            kRequests = 0
            if (now - timeRequests < DateUnit.SEC_IN_MILLS) {
                try {
                    Thread.sleep(400)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            timeRequests = now
        }
    }

    private fun getTitle(line: String?, name: String): String {
        if (line == null) return ""
        var s = line.fromHTML.replace(".20", ".")
        if (s.contains(name)) {
            s = s.substring(9).replace(Const.KV_CLOSE, "")
            if (s.contains("№"))
                s = s.substring(s.indexOf("№"))
            else if (s.contains(Const.KV_OPEN))
                s = s.substring(s.indexOf(Const.KV_OPEN) + 1)
        }
        return s
    }

    fun finish() {
        isFinish = true
        storage.close()
    }
}