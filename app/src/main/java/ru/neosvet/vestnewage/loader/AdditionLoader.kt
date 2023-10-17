package ru.neosvet.vestnewage.loader

import android.content.ContentValues
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.loader.basic.LoadHandlerLite
import ru.neosvet.vestnewage.loader.basic.Loader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.AdditionStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Files
import ru.neosvet.vestnewage.utils.percent
import ru.neosvet.vestnewage.view.list.paging.NeoPaging
import java.io.BufferedReader
import java.io.InputStreamReader

class AdditionLoader(private val client: NeoClient) : Loader {
    private var maxPost = 0

    override fun load() {
        val storage = AdditionStorage()
        storage.open()
        load(storage, 0)
    }

    override fun cancel() {}

    fun loadAll(handler: LoadHandlerLite) {
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

    fun load(storage: AdditionStorage, startId: Int) {
        if (maxPost == 0) {
            loadChanges(storage)
            maxPost = loadMax()
        }
        if (storage.max == 0) storage.findMax()
        if (startId > maxPost) return

        val start = if (startId == 0) {
            val file = Files.dateBase(DataBase.ADDITION)
            file.setLastModified(System.currentTimeMillis())
            maxPost
        } else if (startId < NeoPaging.ON_PAGE) NeoPaging.ON_PAGE
        else startId
        val end = start - NeoPaging.ON_PAGE + 1
        for (i in start downTo end) {
            if (storage.hasPost(i).not())
                storage.insert(loadPost(i))
        }
    }

    private fun loadPost(id: Int): ContentValues {
        val d = (id / 200).toString()
        val stream = client.getStream("${Urls.Addition}$d/$id.txt")
        val br = BufferedReader(InputStreamReader(stream, Const.ENCODING), 1000)
        val row = ContentValues()
        row.put(DataBase.ID, id)
        row.put(Const.TITLE, br.readLine())
        row.put(Const.LINK, br.readLine().toInt())
        row.put(Const.TIME, br.readLine())
        val des = StringBuilder()
        br.forEachLine {
            des.append(it)
            des.append(Const.N)
        }
        br.close()
        row.put(Const.DESCTRIPTION, des.toString().trim())
        return row
    }

    fun checkUpdate(): Boolean {
        val storage = AdditionStorage()
        storage.open()
        loadChanges(storage)
        storage.findMax()
        maxPost = loadMax()
        val has = maxPost > storage.max
        storage.close()
        return has
    }

    private fun loadMax(): Int {
        val stream = client.getStream("${Urls.Addition}max.txt")
        val br = BufferedReader(InputStreamReader(stream, Const.ENCODING), 1000)
        val s = br.readLine()
        br.close()
        return s.toInt()
    }

    private fun loadChanges(storage: AdditionStorage) {
        val baseTime = Files.dateBase(DataBase.ADDITION).lastModified()
        val stream = client.getStream("${Urls.Addition}changed.txt")
        val br = BufferedReader(InputStreamReader(stream, Const.ENCODING), 1000)
        br.forEachLine {
            val i = it.lastIndexOf(" ")
            if (it.substring(i + 1).toLong() > baseTime) {
                val id = it.substring(it.indexOf(" ") + 1, i).toInt()
                if (it.contains("delete"))
                    storage.delete(id)
                else if (it.contains("update")) {
                    val post = loadPost(id)
                    if (storage.update(id, post).not())
                        storage.insert(post)
                }
            }
        }
        br.close()
    }
}