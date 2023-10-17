package ru.neosvet.vestnewage.viewmodel

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.work.Data
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.MarkerItem
import ru.neosvet.vestnewage.loader.page.PageLoader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.storage.MarkersStorage
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Files
import ru.neosvet.vestnewage.utils.fromHTML
import ru.neosvet.vestnewage.view.activity.BrowserActivity
import ru.neosvet.vestnewage.viewmodel.basic.MarkersStrings
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.ListState
import ru.neosvet.vestnewage.viewmodel.state.MarkersState
import java.io.*

class MarkersToiler : NeoToiler() {
    enum class Type {
        NONE, COL, MAR, MOVE, SAVE, LOAD, EXPORT, IMPORT
    }

    private val storage = MarkersStorage()
    private lateinit var strings: MarkersStrings
    private var task: Type = Type.NONE
    private var loadIndex = -1
    private val list = mutableListOf<MarkerItem>()
    private var change = false
    private var collectionData = ""
    private var collectionTitle = ""
    private var isCollections = true
    private val title: String
        get() = if (isCollections) strings.collections else collectionTitle

    override fun getInputData(): Data {
        val builder = Data.Builder()
            .putString(Const.TASK, "Markers")
            .putString(Const.MODE, task.toString())
        if (isCollections)
            builder.putString(Const.TITLE, strings.collections)
        else {
            builder.putString(Const.TITLE, collectionTitle)
            builder.putString(Const.DESCTRIPTION, collectionData)
            if (loadIndex > -1)
                builder.putString(Const.LINK, list[loadIndex].data)
        }
        return builder.build()
    }

    override fun init(context: Context) {
        strings = MarkersStrings(
            collections = context.getString(R.string.collections),
            no_collections = context.getString(R.string.no_collections),
            sel_pos = context.getString(R.string.sel_pos),
            sel_par = context.getString(R.string.sel_par),
            pos_n = context.getString(R.string.pos_n),
            par_n = context.getString(R.string.par_n),
            page_entirely = context.getString(R.string.page_entirely),
            not_load_page = context.getString(R.string.not_load_page),
            unuse_dot = context.getString(R.string.unuse_dot),
            cancel_rename = context.getString(R.string.cancel_rename),
            help_edit = context.getString(R.string.help_edit)
        )
    }

    override suspend fun defaultState() {
        openList()
    }

    override suspend fun doLoad() {
        if (loadIndex == -1) return
        task = Type.LOAD
        val index = loadIndex
        val loader = PageLoader(NeoClient())
        loader.download(list[index].data, true)
        postState(BasicState.Ready)
        loadIndex = -1
        val id = list[index].id.toString()
        getMarker(id)?.let {
            list[index] = it
            postState(ListState.Update(index, list[index]))
            return
        }
        list.removeAt(index)
        postState(ListState.Remove(index))
    }

    override fun onDestroy() {
        storage.close()
    }

    fun restore() {
        if (change) {
            change = false
            openList()
        } else setState(MarkersState.Primary(title, list, isCollections))
    }

    fun save() {
        if (!change) {
            setState(MarkersState.Primary(title, list, isCollections))
            return
        }
        task = Type.SAVE
        change = false
        scope.launch {
            if (isCollections) {
                for (i in 1 until list.size) {
                    val row = ContentValues()
                    row.put(Const.PLACE, i)
                    storage.updateCollection(list[i].id, row)
                }
            } else {
                val s = StringBuilder()
                for (element in list) {
                    s.append(element.id)
                    s.append(Const.COMMA)
                }
                s.delete(s.length - 1, s.length)
                val t = s.toString()
                val row = ContentValues()
                row.put(DataBase.MARKERS, t)
                storage.updateCollectionByTitle(collectionTitle, row)
            }
            postState(MarkersState.Primary(title, list, isCollections))
        }
    }

    fun export(file: String) {
        task = Type.EXPORT
        isRun = true
        scope.launch {
            doExport(Uri.parse(file))
            postState(MarkersState.FinishExport(file))
            isRun = false
        }
    }

    fun import(file: String) {
        task = Type.IMPORT
        isRun = true
        scope.launch {
            doImport(Uri.parse(file))
            postState(MarkersState.FinishImport)
            isRun = false
        }
    }

    fun loadPage(index: Int) {
        loadIndex = index
        load()
    }

    fun openCollectionsList() {
        task = Type.COL
        isCollections = true
        scope.launch {
            list.clear()
            list.add(MarkerItem(-1, strings.help_edit, ""))
            val cursor = storage.getCollections(Const.PLACE)
            var s: String
            var isNull = false
            if (cursor.moveToFirst()) {
                val iID = cursor.getColumnIndex(DataBase.ID)
                val iTitle = cursor.getColumnIndex(Const.TITLE)
                val iMarkers = cursor.getColumnIndex(DataBase.MARKERS)
                do {
                    s = cursor.getString(iMarkers) ?: ""
                    if (s.isEmpty()) isNull = true
                    list.add(
                        MarkerItem(
                            id = cursor.getInt(iID),
                            title = cursor.getString(iTitle),
                            data = s
                        )
                    )
                } while (cursor.moveToNext())
            }
            cursor.close()
            if (isNull && list.size == 1)
                list.clear()
            postState(MarkersState.Primary(strings.collections, list, isCollections))
        }
    }

    fun openMarkersList(iCol: Int = -1) {
        task = Type.MAR
        isCollections = false
        if (iCol > -1) {
            collectionTitle = list[iCol].title
            collectionData = list[iCol].data
        }
        scope.launch {
            list.clear()
            val mId = if (collectionData.contains(Const.COMMA))
                collectionData.split(Const.COMMA)
            else listOf(collectionData)
            for (s in mId) {
                getMarker(s)?.let { list.add(it) }
            }
            postState(MarkersState.Primary(collectionTitle, list, isCollections))
        }
    }

    private fun getMarker(id: String): MarkerItem? {
        val cursor = storage.getMarker(id)
        val item = if (cursor.moveToFirst()) {
            val iID = cursor.getColumnIndex(DataBase.ID)
            val iPlace = cursor.getColumnIndex(Const.PLACE)
            val iLink = cursor.getColumnIndex(Const.LINK)
            val iDes = cursor.getColumnIndex(Const.DESCTRIPTION)
            val link = cursor.getString(iLink)
            val place = cursor.getString(iPlace)
            MarkerItem(
                id = cursor.getInt(iID),
                title = getTitle(link),
                data = link
            ).also {
                it.place = place
                it.des = cursor.getString(iDes).trim()
                val p = getPlace(link, place)
                it.des += Const.N + p.first
                it.text = p.second
            }
        } else null
        cursor.close()
        return item
    }

    @SuppressLint("Range")
    private fun getPlace(link: String, place: String): Pair<String, String> {
        if (place == "0") return Pair(strings.page_entirely, "")
        val storage = PageStorage()
        storage.open(link)
        try {
            val p: String
            val s = if (place.contains("%")) { //позиция
                p = strings.pos_n + place.replace(".", Const.COMMA)
                storage.getContentPage(link, false)?.let {
                    parsePosition(place, it)
                }
            } else { //абзацы
                p = strings.par_n + place.replace(Const.COMMA, ", ")
                val cursor = storage.getPage(link)
                if (cursor.moveToFirst()) {
                    var i = cursor.getColumnIndex(DataBase.ID)
                    i = cursor.getInt(i)
                    parseParagraphs(place, storage.getParagraphs(i))
                } else null
            }
            storage.close()
            s?.let { return Pair(p, it) }
        } catch (ex: Exception) {
            storage.close()
            ex.printStackTrace()
        }
        val p = if (place.contains("%")) strings.sel_pos + place
        else strings.sel_par + MarkersStorage.openList(place).replace(Const.COMMA, ", ")
        return Pair(p, "")
    }

    private fun parseParagraphs(place: String, curPar: Cursor): String? {
        val b = StringBuilder()
        val p = MarkersStorage.closeList(place)
        var i = 1
        if (curPar.moveToFirst()) {
            do {
                if (p.contains(MarkersStorage.closeList(i.toString()))) {
                    b.append(curPar.getString(0).fromHTML)
                    b.append(Const.N)
                    b.append(Const.N)
                }
                i++
            } while (curPar.moveToNext())
        } else { // страница не загружена...
            curPar.close()
            return null
        }
        curPar.close()
        b.delete(b.length - 2, b.length)
        return b.toString()
    }

    private fun parsePosition(place: String, page: String): String {
        val b = StringBuilder()
        b.append(Const.N)
        b.append(page)
        var k = 5 // имитация нижнего "колонтитула" страницы
        var i = b.indexOf(Const.N)
        while (i > -1) {
            k++
            i = b.indexOf(Const.N, i + 1)
        }
        val f = place.substring(0, place.length - 1).replace(Const.COMMA, ".").toFloat() / 100f
        k = (k.toFloat() * f).toInt() + 1
        i = 0
        var u: Int
        do {
            k--
            u = i
            i = b.indexOf(Const.N, u + 1)
        } while (k > 1 && i > -1)
        if (b.substring(u + 1, u + 2) == Const.N) u++
        if (i > -1) i = b.indexOf(Const.N, i + 1)
        if (i > -1) i = b.indexOf(Const.N, i + 1)
        b.delete(0, u + 1)
        i -= u
        if (i > -1) {
            if (b.substring(i - 1, i) == Const.N) i--
            b.delete(i - 1, b.length)
        }
        return b.toString()
    }

    private fun getTitle(link: String): String {
        val storage = PageStorage()
        storage.open(link)
        val t = storage.getContentPage(link, true)
        storage.close()
        return t ?: link
    }

    fun openList() {
        if (isCollections) openCollectionsList()
        else openMarkersList()
    }

    fun delete(index: Int) {
        scope.launch {
            val item = list[index]
            val id = item.id.toString()
            if (isCollections)
                storage.deleteCollection(
                    id, MarkersStorage.getList(item.data),
                    strings.no_collections
                )
            else storage.deleteMarker(id)
            list.removeAt(index)
            postState(ListState.Remove(index))
        }
    }

    fun updateMarker(index: Int) {
        scope.launch {
            val colCursor = storage.getMarkersListByTitle(collectionTitle)
            collectionData = if (colCursor.moveToFirst())
                colCursor.getString(0) //список закладок в подборке
            else ""
            colCursor.close()
            val id = list[index].id.toString()
            if ((collectionData + Const.COMMA).contains(id + Const.COMMA))
                getMarker(id)?.let {
                    list[index] = it
                    postState(ListState.Update(index, list[index]))
                    return@launch
                }
            list.removeAt(index)
            postState(ListState.Remove(index))
        }
    }

    fun move(fromIndex: Int, toIndex: Int) {
        val n = if (isCollections) 0 else -1
        if (fromIndex == n || toIndex == list.size) return
        task = Type.MOVE
        change = true
        val item = list[toIndex]
        list.removeAt(toIndex)
        list.add(fromIndex, item)
        setState(ListState.Move(fromIndex, toIndex))
    }

    fun rename(index: Int, name: String) {
        var isCancel = name.isEmpty()
        if (!isCancel) {
            if (name.contains(Const.COMMA)) {
                setState(BasicState.Message(strings.unuse_dot))
                return
            }
            for (i in 0 until list.size) {
                if (name == list[i].title) {
                    isCancel = true
                    break
                }
            }
        }
        if (isCancel) {
            setState(BasicState.Message(strings.cancel_rename))
            return
        }
        val row = ContentValues()
        row.put(Const.TITLE, name)
        if (storage.updateCollection(list[index].id, row)) {
            list[index].title = name
            setState(ListState.Update(index, list[index]))
        } else
            setState(BasicState.Message(strings.cancel_rename))
    }

    private fun doExport(file: Uri) {
        var i1: Int
        var i2: Int
        var i3: Int
        var cursor = storage.getCollections(DataBase.ID)
        val outStream = App.context.contentResolver.openOutputStream(file)
        val bw = BufferedWriter(OutputStreamWriter(outStream, Const.ENCODING))
        if (cursor.moveToFirst()) {
            i1 = cursor.getColumnIndex(DataBase.ID)
            i2 = cursor.getColumnIndex(Const.TITLE)
            i3 = cursor.getColumnIndex(DataBase.MARKERS)
            do {
                bw.write(cursor.getString(i1) + Const.N)
                bw.write(cursor.getString(i2) + Const.N)
                bw.write(cursor.getString(i3) + Const.N)
                bw.flush()
            } while (cursor.moveToNext())
        }
        cursor.close()
        bw.write(Const.AND + Const.N)
        cursor = storage.getMarkers()
        val i4: Int
        val i5: Int
        if (cursor.moveToFirst()) {
            i1 = cursor.getColumnIndex(DataBase.ID)
            i2 = cursor.getColumnIndex(Const.PLACE)
            i3 = cursor.getColumnIndex(Const.LINK)
            i4 = cursor.getColumnIndex(Const.DESCTRIPTION)
            i5 = cursor.getColumnIndex(DataBase.COLLECTIONS)
            do {
                bw.write(cursor.getString(i1) + Const.N)
                bw.write(cursor.getString(i2) + Const.N)
                bw.write(cursor.getString(i3) + Const.N)
                bw.write(cursor.getString(i4) + Const.N)
                bw.write(cursor.getString(i5) + Const.N)
                bw.flush()
            } while (cursor.moveToNext())
        }
        cursor.close()
        bw.close()
        outStream!!.close()
        cursor.close()
    }

    private fun doImport(file: Uri) {
        var inputStream = App.context.contentResolver.openInputStream(file)
        var br = BufferedReader(InputStreamReader(inputStream, Const.ENCODING), 1000)
        var id: Int
        var nid: Int
        var cursor: Cursor
        var row: ContentValues
        //определение новых id для подборок
        val hC = HashMap<Int, Int>()
        var s: String? = br.readLine()
        while (s != null) {
            if (s == Const.AND) break
            id = s.toInt()
            s = br.readLine() //title
            cursor = storage.getMarkersListByTitle(s)
            if (cursor.moveToFirst()) {
                nid = cursor.getInt(0)
            } else {
                row = ContentValues()
                row.put(Const.TITLE, s)
                nid = storage.insertCollection(row).toInt()
            }
            hC[id] = nid
            cursor.close()
            br.readLine() //markers
            s = br.readLine()
        }
        //определение новых id для закладок
        val hM = HashMap<Int, Int>()
        var p: String
        var d: String
        s = br.readLine()
        while (s != null) {
            id = s.toInt()
            p = br.readLine()
            s = br.readLine()
            d = br.readLine()
            br.readLine() //col
            nid = storage.foundMarker(arrayOf(p, s, d))
            if (nid == -1) {
                row = ContentValues()
                row.put(Const.PLACE, p)
                row.put(Const.LINK, s)
                row.put(Const.DESCTRIPTION, d)
                nid = storage.insertMarker(row).toInt()
            }
            hM[id] = nid
            s = br.readLine()
        }
        br.close()
        inputStream!!.close()
        //изменение id в подборках
        inputStream = App.context.contentResolver.openInputStream(file)
        br = BufferedReader(InputStreamReader(inputStream, Const.ENCODING), 1000)
        val f = Files.getFileS(DataBase.MARKERS)
        val bw = BufferedWriter(OutputStreamWriter(FileOutputStream(f), Const.ENCODING))
        s = br.readLine()
        while (s != null) {
            if (s == Const.AND) break
            id = s.toInt()
            bw.write(hC[id].toString() + Const.N)
            br.readLine() //title
            s = br.readLine()
            bw.write(getNewId(hM, MarkersStorage.getList(s)) + Const.N) //markers
            bw.flush()
            s = br.readLine()
        }
        //изменение id в закладках
        bw.write(s + Const.N)
        s = br.readLine()
        while (s != null) {
            id = s.toInt()
            bw.write(hM[id].toString() + Const.N)
            br.readLine() //place
            br.readLine() //link
            br.readLine() //des
            s = br.readLine()
            bw.write(getNewId(hC, MarkersStorage.getList(s)) + Const.N) //col
            bw.flush()
            s = br.readLine()
        }
        bw.close()
        br.close()
        inputStream!!.close()
        hC.clear()
        hM.clear()
        //совмещение подборок
        br = BufferedReader(InputStreamReader(FileInputStream(f), Const.ENCODING), 1000)
        s = br.readLine()
        while (s != null) {
            if (s == Const.AND) break
            cursor = storage.getMarkersList(s)
            if (cursor.moveToFirst()) {
                p = br.readLine()
                row = ContentValues()
                row.put(
                    DataBase.MARKERS,
                    combineIds(cursor.getString(0), MarkersStorage.getList(p))
                )
                storage.updateCollection(s.toInt(), row)
            }
            cursor.close()
            s = br.readLine()
        }
        //совмещение закладок
        s = br.readLine()
        while (s != null) {
            cursor = storage.getMarkerCollections(s)
            if (cursor.moveToFirst()) {
                p = br.readLine()
                row = ContentValues()
                row.put(
                    DataBase.COLLECTIONS,
                    combineIds(cursor.getString(0), MarkersStorage.getList(p))
                )
                storage.updateMarker(s, row)
            }
            cursor.close()
            s = br.readLine()
        }
        br.close()
        f.delete()
    }

    private fun combineIds(ids: String?, m: Array<String>): String {
        val b: StringBuilder
        if (ids == null) {
            b = StringBuilder()
            for (s in m) {
                b.append(Const.COMMA)
                b.append(s)
            }
            b.delete(0, 1)
        } else {
            b = StringBuilder(ids)
            val list = MarkersStorage.closeList(ids)
            for (s in m) {
                if (!list.contains(MarkersStorage.closeList(s))) {
                    b.append(Const.COMMA)
                    b.append(s)
                }
            }
        }
        return b.toString()
    }

    private fun getNewId(h: HashMap<Int, Int>, m: Array<String>): String {
        val b = java.lang.StringBuilder()
        for (s in m) {
            b.append(h[s.toInt()])
            b.append(Const.COMMA)
        }
        b.delete(b.length - 1, b.length)
        return b.toString()
    }

    fun edit() {
        setState(MarkersState.Primary(title, list, isCollections, true))
    }

    fun openPage(index: Int) {
        BrowserActivity.openReader(list[index].data, list[index].text)
    }
}