package ru.neosvet.vestnewage.loader.page

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
        var id = 0
        var s: String? = page.currentElem
        var wasHead = false
        val time = System.currentTimeMillis()
        do {
            if (page.isHead && (!wasHead || !storage.isArticle)) {
                wasHead = true
                id = storage.putTitle(getTitle(s, storage.name), link, time)
                if (exists) storage.deleteParagraphs(id)
                s = page.nextElem
            }
            s?.let {
                if (it.fromHTML.isNotEmpty())
                    storage.insertParagraph(id, it)
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
            s = s.substring(9)
            if (s.contains(Const.KV_OPEN))
                s = s.substring(s.indexOf(Const.KV_OPEN) + 1, s.length - 1)
        }
        return s
    }

    fun finish() {
        isFinish = true
        storage.close()
    }
}