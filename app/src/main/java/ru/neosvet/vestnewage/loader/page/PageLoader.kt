package ru.neosvet.vestnewage.loader.page

import android.content.ContentValues
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.loader.basic.Loader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
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
        if (!singlePage && storage.existsPage(link)) {
            return false
        }
        if (storage.isDoctrine) {
            downloadDoctrinePage(link)
            if (singlePage) storage.close()
            return true
        }
        if (!singlePage) checkRequests()
        val page = PageParser(client)
        page.load(Urls.Page + link, "")
        if (singlePage) storage.deleteParagraphs(storage.getPageId(link))
        var row: ContentValues
        var id = 0
        var bid = 0
        var s: String? = page.currentElem
        do {
            if (page.isHead) {
                id = storage.getPageId(link)
                row = ContentValues()
                row.put(Const.TIME, System.currentTimeMillis())
                if (id == -1) { // id не найден, материала нет - добавляем
                    if (link.contains("#")) {
                        id = bid
                        row = ContentValues()
                        row.put(DataBase.ID, id)
                        row.put(DataBase.PARAGRAPH, s)
                        storage.insertParagraph(row)
                    } else {
                        row.put(Const.TITLE, getTitle(s, storage.name))
                        row.put(Const.LINK, link)
                        id = storage.insertTitle(row).toInt()
                        //обновляем дату изменения списка:
                        row = ContentValues()
                        row.put(Const.TIME, System.currentTimeMillis())
                        storage.updateTitle(1, row)
                    }
                } else { // id найден, значит материал есть
                    //обновляем заголовок
                    row.put(Const.TITLE, getTitle(s, storage.name))
                    //обновляем дату загрузки материала
                    storage.updateTitle(id, row)
                    //удаляем содержимое материала
                    storage.deleteParagraphs(id)
                }
                bid = id
                s = page.nextElem
            }
            if (!isEmpty(s)) {
                row = ContentValues()
                row.put(DataBase.ID, id)
                row.put(DataBase.PARAGRAPH, s)
                storage.insertParagraph(row)
            }
            s = page.nextElem
        } while (s != null)
        if (singlePage) finish()
        page.clear()
        return true
    }

    private fun downloadDoctrinePage(link: String) {
        var s: String? = link.substring(Const.DOCTRINE.length) //pages
        val stream = client.getStream("${Urls.DoctrineBase}$s.txt")
        val br = BufferedReader(InputStreamReader(stream, Const.ENCODING), 1000)
        val time = br.readLine().toLong()
        var row = ContentValues()
        row.put(Const.TIME, time)
        storage.updateTitle(link, row)
        val id = storage.getPageId(link)
        storage.deleteParagraphs(id)
        s = br.readLine()
        while (s != null) {
            row = ContentValues()
            row.put(DataBase.ID, id)
            row.put(DataBase.PARAGRAPH, s)
            storage.insertParagraph(row)
            s = br.readLine()
        }
        br.close()
    }

    private fun isEmpty(s: String?): Boolean {
        return Lib.withOutTags(s).isEmpty()
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
        var s = Lib.withOutTags(line).replace(".20", ".")
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