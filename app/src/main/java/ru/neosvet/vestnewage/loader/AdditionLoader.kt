package ru.neosvet.vestnewage.loader

import android.content.ContentValues
import ru.neosvet.vestnewage.data.NeoException
import ru.neosvet.vestnewage.loader.basic.LoadHandlerLite
import ru.neosvet.vestnewage.loader.basic.Loader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.AdditionStorage
import ru.neosvet.vestnewage.storage.DataBase
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Files
import ru.neosvet.vestnewage.utils.percent
import ru.neosvet.vestnewage.view.list.paging.NeoPaging
import java.io.BufferedReader
import java.io.InputStreamReader

class AdditionLoader(private val client: NeoClient) : Loader {
    private var maxPost = 0
    private var isRun = false

    override fun load() {
        val storage = AdditionStorage()
        storage.open()
        load(storage, 0)
    }

    override fun cancel() {}

    fun loadAll(handler: LoadHandlerLite?) {
        isRun = true
        val storage = AdditionStorage()
        storage.open()
        if (maxPost == 0)
            maxPost = loadMax()
        var stream = client.getStream("${Urls.Addition}archive/max.txt")
        var br = BufferedReader(InputStreamReader(stream, Const.ENCODING), 1000)
        var s: String? = br.readLine()
        br.close()
        val max = s?.toInt() ?: 0
        val des = StringBuilder()
        var id = 0
        for (i in 0..max) {
            stream = client.getStream("${Urls.Addition}archive/$i.txt")
            br = BufferedReader(InputStreamReader(stream, Const.ENCODING), 1000)
            s = br.readLine()
            while (s != null && isRun) {
                val row = ContentValues()
                id = s.toInt()
                row.put(DataBase.ID, id)
                row.put(Const.TITLE, br.readLine())
                row.put(Const.LINK, br.readLine().toInt())
                row.put(Const.TIME, br.readLine())
                s = br.readLine()
                while (s != null && s != Const.END) {
                    des.append(s)
                    des.append(Const.N)
                    s = br.readLine()
                }
                row.put(Const.DESCRIPTION, des.toString().trim())
                des.clear()
                if (!storage.update(id, row))
                    storage.insert(row)
                handler?.postPercent(id.percent(maxPost))
                s = br.readLine()
            }
            br.close()
            if (!isRun) break
        }
        id++
        while (id <= maxPost && isRun) {
            if (storage.hasPost(id).not())
                loadPost(id)?.let { storage.insert(it) }
            id++
            handler?.postPercent(id.percent(maxPost))
            if (!isRun) break
        }
        storage.close()
        isRun = false
    }

    fun load(storage: AdditionStorage, startId: Int) {
        isRun = true
        try {
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
            for (p in start downTo end) {
                if (storage.hasPost(p).not())
                    loadPost(p)?.let { storage.insert(it) }
                if (!isRun) break
            }
        } catch (_: NeoException.SiteNoResponse) {
        }
        isRun = false
    }

    private fun loadPost(id: Int): ContentValues? {
        try {
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
            row.put(Const.DESCRIPTION, des.toString().trim())
            return row
        } catch (_: NeoException.SiteCode) {
        }
        return null
    }

    fun checkUpdate(): Boolean {
        val storage = AdditionStorage()
        storage.open()
        loadChanges(storage)
        storage.findMax()
        maxPost = loadMax()
        val has = maxPost > storage.max
        if (has) load(storage, maxPost)
        storage.close()
        if (!has) {
            val file = Files.dateBase(DataBase.ADDITION)
            file.setLastModified(System.currentTimeMillis())
        }
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
                    loadPost(id)?.let {
                        if (storage.update(id, it).not())
                            storage.insert(it)
                    }
                }
            }
        }
        br.close()
    }
}