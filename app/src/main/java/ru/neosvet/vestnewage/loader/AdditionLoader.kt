package ru.neosvet.vestnewage.loader

import android.content.ContentValues
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.loader.basic.LoadHandlerLite
import ru.neosvet.vestnewage.loader.basic.Loader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.NetConst
import ru.neosvet.vestnewage.storage.AdditionStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
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
        var n = if (storage.max > 0) storage.max
        else maxPost - NeoPaging.ON_PAGE
        if (n == maxPost) {
            val file = Lib.getFileDB(DataBase.ADDITION)
            file.setLastModified(System.currentTimeMillis())
            return
        }
        while (n < maxPost) {
            n++
            if (storage.hasPost(n).not())
                storage.insert(loadPost(n))
        }
        if (startId >= maxPost || startId == 0) return
        var end = startId - NeoPaging.ON_PAGE + 1
        if (end < 1) end = 1
        for (i in startId downTo end) {
            if (storage.hasPost(i).not())
                storage.insert(loadPost(i))
        }
    }

    private val additionUrl: String
        get() = if (NeoClient.isSiteCom) NetConst.ADDITION_URL_COM else NetConst.ADDITION_URL

    private fun loadPost(id: Int): ContentValues {
        val d = (id / 200).toString()
        val stream = client.getStream("$additionUrl$d/$id.txt")
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

    fun loadMax(): Int {
        val stream = client.getStream("${additionUrl}max.txt")
        val br = BufferedReader(InputStreamReader(stream, Const.ENCODING), 1000)
        val s = br.readLine()
        br.close()
        return s.toInt()
    }

    private fun loadChanges(storage: AdditionStorage) {
        val baseTime = Lib.getFileDB(DataBase.ADDITION).lastModified()
        val stream = client.getStream("${additionUrl}changed.txt")
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