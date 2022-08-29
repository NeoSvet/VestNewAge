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
import ru.neosvet.vestnewage.storage.MarkersStorage
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.view.activity.BrowserActivity
import ru.neosvet.vestnewage.viewmodel.basic.ListEvent
import ru.neosvet.vestnewage.viewmodel.basic.MarkersStrings
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import java.io.*

class MarkersToiler : NeoToiler() {
    enum class Type {
        NONE, LIST, PAGE, FILE
    }

    private val storage = MarkersStorage()
    var iSel = -1
        private set
    var change = false
    private var sCol: String? = null
    private lateinit var strings: MarkersStrings
    private var task: Type = Type.NONE
    val list = mutableListOf<MarkerItem>()
    val title: String
        get() = if (sCol == null) strings.collections
        else sCol!!.substring(0, sCol!!.indexOf(Const.N))
    val workOnFile: Boolean
        get() = task == Type.FILE
    val isCollections: Boolean
        get() = sCol == null
    private var page: String? = null
    val selectedItem: MarkerItem?
        get() = if (iSel == -1) null else list[iSel]
    private var isInit = false

    fun init(context: Context) {
        if (isInit) return
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
            cancel_rename = context.getString(R.string.cancel_rename)
        )
        isInit = true
    }

    override suspend fun doLoad() {
        page?.let { link ->
            task = Type.PAGE
            val loader = PageLoader()
            loader.download(link, true)
            page = null
            openList()
        }
    }

    override fun onDestroy() {
        storage.close()
    }

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, "Markers")
        .putString(Const.MODE, task.toString())
        .putString(Const.TITLE, sCol)
        .build()

    fun saveChange() {
        if (change.not()) return
        change = false
        if (isCollections) {
            for (i in 1 until list.size) {
                val row = ContentValues()
                row.put(Const.PLACE, i)
                storage.updateCollection(list[i].id, row)
            }
            return
        }
        val s = StringBuilder()
        for (element in list) {
            s.append(element.id)
            s.append(Const.COMMA)
        }
        s.delete(s.length - 1, s.length)
        val t = s.toString()
        val row = ContentValues()
        row.put(DataBase.MARKERS, t)
        sCol?.let {
            val title = it.substring(0, it.indexOf(Const.N))
            storage.updateCollectionByTitle(title, row)
            sCol = it.substring(0, it.indexOf(Const.N) + 1) + t
        }
    }

    fun startExport(file: String) {
        task = Type.FILE
        isRun = true
        scope.launch {
            doExport(Uri.parse(file))
            postState(NeoState.Message(file))
            isRun = false
        }
    }

    fun startImport(file: String) {
        task = Type.FILE
        isRun = true
        scope.launch {
            doImport(Uri.parse(file))
            postState(NeoState.Ready)
            isRun = false
        }
    }

    private fun loadPage(index: Int) {
        page = list[index].data
        load()
    }

    fun openColList() {
        task = Type.LIST
        sCol = null
        scope.launch {
            list.clear()
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
            if (isNull && list.size == 1) {
                list.clear()
                iSel = -1
            }
            postState(NeoState.ListState(ListEvent.RELOAD))
        }
    }

    private fun openMarList(iCol: Int = -1) {
        task = Type.LIST
        if (iCol > -1)
            sCol = list[iCol].title + Const.N + list[iCol].data
        scope.launch {
            list.clear()
            var iID: Int
            var iPlace: Int
            var iLink: Int
            var iDes: Int
            val mId: Array<String>
            var link = sCol!!.substring(sCol!!.indexOf(Const.N) + 1)
            mId = if (link.contains(Const.COMMA)) {
                link.split(Const.COMMA).toTypedArray()
            } else arrayOf(link)
            var cursor: Cursor
            var place: String
            var k = 0
            for (s in mId) {
                cursor = storage.getMarker(s)
                if (cursor.moveToFirst()) {
                    iID = cursor.getColumnIndex(DataBase.ID)
                    iPlace = cursor.getColumnIndex(Const.PLACE)
                    iLink = cursor.getColumnIndex(Const.LINK)
                    iDes = cursor.getColumnIndex(Const.DESCTRIPTION)
                    link = cursor.getString(iLink)
                    place = cursor.getString(iPlace)
                    val item = MarkerItem(
                        id = cursor.getInt(iID),
                        title = getTitle(link),
                        data = link
                    )
                    item.place = place
                    item.des = if (cursor.getString(iDes).isEmpty()) getPlace(link, place)
                    else cursor.getString(iDes) + Const.N + getPlace(link, place)
                    k++
                    list.add(item)
                }
                cursor.close()
            }
            postState(NeoState.ListState(ListEvent.RELOAD))
        }
    }

    @SuppressLint("Range")
    private fun getPlace(link: String, place: String): String {
        if (place == "0") return strings.page_entirely
        try {
            val storage = PageStorage()
            storage.open(link)
            val s = if (place.contains("%")) { //позиция
                storage.getContentPage(link, false)?.let {
                    parsePosition(place, it)
                } ?: strings.not_load_page
            } else { //абзацы
                val cursor = storage.getPage(link)
                if (cursor.moveToFirst()) {
                    var i = cursor.getColumnIndex(DataBase.ID)
                    i = cursor.getInt(i)
                    parseParagraphs(place, storage.getParagraphs(i)) ?: strings.not_load_page
                } else
                    strings.not_load_page
            }
            storage.close()
            if (s.isNotEmpty()) return s
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return if (place.contains("%"))
            strings.sel_pos + place
        else
            strings.sel_par + MarkersStorage.openList(place).replace(Const.COMMA, ", ")
    }

    private fun parseParagraphs(place: String, curPar: Cursor): String? {
        val b = StringBuilder()
        b.append(strings.par_n)
        b.append(place.replace(Const.COMMA, ", "))
        b.append(":")
        b.append(Const.N)
        val p = MarkersStorage.closeList(place)
        var i = 1
        if (curPar.moveToFirst()) {
            do {
                if (p.contains(MarkersStorage.closeList(i.toString()))) {
                    b.append(Lib.withOutTags(curPar.getString(0)))
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

    private fun parsePosition(p: String, page: String): String {
        val b = StringBuilder()
        b.append(Const.N)
        b.append(page)
        var k = 5 // имитация нижнего "колонтитула" страницы
        var i = b.indexOf(Const.N)
        while (i > -1) {
            k++
            i = b.indexOf(Const.N, i + 1)
        }
        val f = p.substring(0, p.length - 1).replace(Const.COMMA, ".").toFloat() / 100f
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
        b.insert(0, strings.pos_n + p.replace(".", Const.COMMA) + ":" + Const.N)
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
        iSel = -1
        if (sCol == null) openColList()
        else openMarList()
    }

    fun canEdit(): Boolean {
        if (list.isEmpty())
            return false
        iSel = if (isCollections) {
            if (list.size == 1)
                return false
            1
        } else 0
        return true
    }

    fun deleteSelected() {
        scope.launch {
            val item = list[iSel]
            val id = item.id.toString()
            val n: Int
            if (isCollections) { //удаляем подборку
                n = 1
                storage.deleteCollection(
                    id, MarkersStorage.getList(item.data),
                    strings.no_collections
                )
            } else { //удаляем закладку
                n = 0
                storage.deleteMarker(id)
            }
            val index = iSel
            list.removeAt(index)
            if (list.size == n) iSel = -1
            else if (list.size == iSel) iSel--
            postState(NeoState.ListState(ListEvent.REMOTE, index))
        }
    }

    fun updateMarkersList() {
        sCol = sCol!!.substring(0, sCol!!.indexOf(Const.N))
        val cursor = storage.getMarkersListByTitle(sCol!!)
        sCol += if (cursor.moveToFirst())
            Const.N + cursor.getString(0) //список закладок в подборке
        else Const.N
        cursor.close()
        openMarList()
    }

    fun moveToTop() {
        var n = 0
        if (isCollections) n = 1
        if (iSel == n)
            return
        task = Type.LIST
        change = true
        n = iSel - 1
        val item = list[n]
        list.removeAt(n)
        list.add(iSel, item)
        iSel = n
        setState(NeoState.ListState(ListEvent.MOVE, n + 1))
    }

    fun moveToBottom() {
        if (iSel == list.size - 1)
            return
        task = Type.LIST
        change = true
        val n = iSel + 1
        val item = list[n]
        list.removeAt(n)
        list.add(iSel, item)
        iSel = n
        setState(NeoState.ListState(ListEvent.MOVE, n - 1))
    }

    fun renameSelected(name: String) {
        var bCancel = name.isEmpty()
        if (!bCancel) {
            if (name.contains(Const.COMMA)) {
                setState(NeoState.Message(strings.unuse_dot))
                return
            }
            for (i in 0 until list.size) {
                if (name == list[i].title) {
                    bCancel = true
                    break
                }
            }
        }
        if (bCancel) {
            setState(NeoState.Message(strings.cancel_rename))
            return
        }
        val row = ContentValues()
        row.put(Const.TITLE, name)
        if (storage.updateCollection(list[iSel].id, row)) {
            list[iSel].title = name
            setState(NeoState.ListState(ListEvent.CHANGE))
        } else
            setState(NeoState.Message(strings.cancel_rename))
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
        val f = Lib.getFileS(DataBase.MARKERS)
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

    fun selected(index: Int) {
        iSel = index
    }

    private fun getPlace(index: Int): String? {
        return if (list[index].place == "0") null
        else list[index].des.let { d ->
            d.substring(d.indexOf(Const.N, d.indexOf(Const.N) + 1) + 1)
        }
    }

    fun onClick(index: Int) {
        if (isRun) return
        if (iSel > -1) {
            if (isCollections && index == 0) return // вне подборок
            selected(index)
            return
        }
        when {
            isCollections ->
                openMarList(index)
            list[index].title.contains("/") ->
                loadPage(index)
            else ->
                BrowserActivity.openReader(list[index].data, getPlace(index))
        }
    }
}