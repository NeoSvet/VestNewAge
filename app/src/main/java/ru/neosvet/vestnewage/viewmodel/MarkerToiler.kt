package ru.neosvet.vestnewage.viewmodel

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import androidx.work.Data
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.CheckItem
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.helper.MarkerHelper
import ru.neosvet.vestnewage.storage.MarkersStorage
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.viewmodel.basic.MarkerStrings
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.basic.Ready
import ru.neosvet.vestnewage.viewmodel.basic.Success

class MarkerToiler : NeoToiler() {
    enum class Type {
        NONE, OPEN_PAGE, OPEN_COLS, ADD_COL, NEW_MARKER,
        OPEN_MARKER, GET_MARKER, ADD_MARKER, UPDATE_MARKER
    }

    private val storage = MarkersStorage()
    private lateinit var strings: MarkerStrings
    private var link: String = ""
    private var id: Int = -1
    private var task: Type = Type.NONE
    private var isInit = false
    lateinit var helper: MarkerHelper
        private set

    fun init(context: Context) {
        if (isInit) return
        isInit = true
        strings = MarkerStrings(
            sel_pos = context.getString(R.string.sel_pos),
            sel_par = context.getString(R.string.sel_par),
            sel_col = context.getString(R.string.sel_col),
            page_entirely = context.getString(R.string.page_entirely),
            unuse_dot = context.getString(R.string.unuse_dot),
            title_already_used = context.getString(R.string.title_already_used),
            no_collections = context.getString(R.string.no_collections)
        )
        helper = MarkerHelper(strings)
    }

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, "Marker")
        .putString(Const.MODE, task.toString())
        .putString(Const.TITLE, "$id $link")
        .putString(Const.DESCTRIPTION, helper.toJson())
        .build()

    override fun onDestroy() {
        storage.close()
    }

    fun open(intent: Intent) {
        link = intent.getStringExtra(Const.LINK) ?: ""
        id = intent.getIntExtra(DataBase.ID, -1)
        scope.launch {
            helper.title = openPage(link)
            openCols()
            if (id == -1)
                newMarker(intent)
            else
                openMarker(id)
            mstate.postValue(Success)
        }
    }

    private fun newMarker(intent: Intent) = helper.run {
        task = Type.NEW_MARKER
        des = if (intent.hasExtra(Const.DESCTRIPTION))
            intent.getStringExtra(Const.DESCTRIPTION) ?: ""
        else {
            val d = DateUnit.initNow()
            d.toString()
        }
        cols = strings.sel_col + strings.no_collections
        colsList[0].isChecked = true
        when {
            intent.hasExtra(Const.PLACE) -> {
                isPar = false
                pos = intent.getFloatExtra(Const.PLACE, 0f)
                updateSel()
            }
            intent.hasExtra(DataBase.PARAGRAPH) -> {
                isPar = true
                val par = intent.getIntExtra(DataBase.PARAGRAPH, 0)
                if (par == 0) {
                    sel = strings.page_entirely
                    setParList()
                } else {
                    parsList[par].isChecked = true
                    updateSel()
                }
            }
            else -> {
                isPar = true
                intent.getStringExtra(Const.PAGE)?.let {
                    it.split(", ").forEach { s ->
                        parsList[s.toInt()].isChecked = true
                    }
                    updateSel()
                    return@run
                }
                sel = strings.page_entirely
                setParList()
            }
        }
    }

    fun finish(des: String) {
        scope.launch {
            val row = getMarkerValues(des)
            if (id > -1) //edit helper
                updateMarker(id, row)
            else
                addMarker(row)
            mstate.postValue(Ready)
        }
    }

    private fun openPage(link: String): String = helper.run { //return title
        task = Type.OPEN_PAGE
        parsList.clear()
        countPar = 5 // имитация нижнего "колонтитула" страницы
        val storage = PageStorage()
        storage.open(link)
        content = storage.getContentPage(link, false) ?: ""
        storage.close()
        if (content.isEmpty()) // страница не загружена...
            return ""
        val m = content.split(Const.NN).toTypedArray()
        var i: Int
        i = 0
        while (i < m.size) {
            parsList.add(
                CheckItem(
                    id = i,
                    title = m[i]
                )
            )
            i++
        }
        i = content.indexOf(Const.N)
        while (i > -1) {
            countPar++
            i = content.indexOf(Const.N, i + 1)
        }
        m[0]
    }

    private fun openCols() = helper.run {
        task = Type.OPEN_COLS
        colsList.clear()
        val cursor = storage.getCollectionsTitle()
        if (cursor.moveToFirst()) {
            val iID = cursor.getColumnIndex(DataBase.ID)
            val iTitle = cursor.getColumnIndex(Const.TITLE)
            do {
                colsList.add(
                    CheckItem(
                        id = cursor.getInt(iID),
                        title = cursor.getString(iTitle)
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
    }

    private fun openMarker(id: Int) {
        task = Type.OPEN_MARKER
        var cursor = storage.getMarker(id.toString())
        cursor.moveToFirst()
        val iDes = cursor.getColumnIndex(Const.DESCTRIPTION)
        helper.des = cursor.getString(iDes) ?: ""
        val iPlace = cursor.getColumnIndex(Const.PLACE)
        var s = cursor.getString(iPlace) ?: ""
        helper.setPlace(s)
        val iCols = cursor.getColumnIndex(DataBase.COLLECTIONS)
        s = cursor.getString(iCols)
        cursor.close()
        val b = StringBuilder(strings.sel_col)
        var iTitle: Int
        for (i in MarkersStorage.getList(s)) {
            cursor = storage.getCollection(i)
            if (cursor.moveToFirst()) {
                iTitle = cursor.getColumnIndex(Const.TITLE)
                b.append(cursor.getString(iTitle))
                b.append(", ")
            }
            cursor.close()
        }
        b.delete(b.length - 2, b.length)
        helper.cols = b.toString()
        helper.setColList()
    }

    fun addCol(title: String) {
        task = Type.ADD_COL
        scope.launch {
            var row: ContentValues
            //освобождаем первую позицию (PLACE) путем смещения всех вперед..
            var cursor = storage.getCollectionsPlace()
            if (cursor.moveToFirst()) {
                val iID = cursor.getColumnIndex(DataBase.ID)
                val iPlace = cursor.getColumnIndex(Const.PLACE)
                var id: Int
                var i: Int
                do {
                    i = cursor.getInt(iPlace)
                    if (i > 0) { // нулевую позицию не трогаем ("Вне подборок")
                        id = cursor.getInt(iID)
                        row = ContentValues()
                        row.put(Const.PLACE, i + 1)
                        storage.updateCollection(id, row)
                    }
                } while (cursor.moveToNext())
            }
            cursor.close()
            //добавляем новую подборку на первую позицию
            row = ContentValues()
            row.put(Const.PLACE, 1)
            row.put(Const.TITLE, title)
            storage.insertCollection(row)
            cursor = storage.getCollectionByPlace(1)
            if (cursor.moveToFirst()) {
                val iId = cursor.getColumnIndex(DataBase.ID)
                val iTitle = cursor.getColumnIndex(Const.TITLE)
                helper.colsList.add(1,
                    CheckItem(
                        id = cursor.getInt(iId),
                        title = cursor.getString(iTitle),
                        isChecked = true
                    )
                )
            }
            cursor.close()
        }
    }

    private fun updateMarker(id: Int, marker: ContentValues) {
        task = Type.UPDATE_MARKER
        //обновляем закладку в базе
        storage.updateMarker(id.toString(), marker)
        //обновляем подборки
        val sid = MarkersStorage.closeList(id.toString())
        helper.colsList.forEach { item ->
            //получаем список закладок в подборке
            val cursor = storage.getMarkersList(item.id.toString())
            if (cursor.moveToFirst()) {
                var s = cursor.getString(0) ?: "" //список закладок в подборке
                if (item.isChecked) { //в этой подоборке должна быть
                    if (!MarkersStorage.closeList(s).contains(sid)) { //отсутствует - добавляем
                        if (s.isNotEmpty()) s = Const.COMMA + s
                        //добавляем новую закладку в самое начало
                        val row = ContentValues()
                        row.put(DataBase.MARKERS, id.toString() + s)
                        storage.updateCollection(item.id, row)
                    }
                } else { //в этой подоборке не должна быть
                    s = MarkersStorage.closeList(s)
                    if (s.contains(sid)) { //присутствует - удаляем
                        s = s.replace(MarkersStorage.closeList(id.toString()), "")
                        s = MarkersStorage.openList(s)
                        //обновляем подборку
                        val row = ContentValues()
                        row.put(DataBase.MARKERS, s)
                        storage.updateCollection(item.id, row)
                    }
                }
            }
            cursor.close()
        }
    }

    private fun addMarker(marker: ContentValues) {
        task = Type.ADD_MARKER
        //добавляем в базу и получаем id
        val marId = storage.insertMarker(marker)
        //обновляем подборки, в которые добавлена закладка
        helper.colsList.forEach { item ->
            if (item.isChecked) {
                //получаем список закладок в подборке
                val cursor = storage.getMarkersList(item.id.toString())
                if (cursor.moveToFirst()) {
                    var s = cursor.getString(0) ?: "" //список закладок в подборке
                    //добавляем новую закладку в самое начало
                    s = if (s.isEmpty()) marId.toString()
                    else marId.toString() + Const.COMMA + s
                    val row = ContentValues()
                    row.put(DataBase.MARKERS, s)
                    storage.updateCollection(item.id, row)
                }
                cursor.close()
            }
        }
    }

    private fun getMarkerValues(des: String): ContentValues {
        task = Type.GET_MARKER
        val row = ContentValues()
        row.put(Const.LINK, link)
        row.put(Const.DESCTRIPTION, des)
        //определяем место
        var s = helper.sel
        s = if (s.contains(":")) s.substring(s.indexOf(":") + 2)
            .replace(", ", Const.COMMA) else "0"
        row.put(Const.PLACE, s)
        //формулируем список подборок
        helper.setColList()
        val b = StringBuilder()
        helper.colsList.forEach { item ->
            if (item.isChecked) {
                b.append(item.id)
                b.append(Const.COMMA)
            }
        }
        b.delete(b.length - 1, b.length)
        row.put(DataBase.COLLECTIONS, b.toString())
        return row
    }
}