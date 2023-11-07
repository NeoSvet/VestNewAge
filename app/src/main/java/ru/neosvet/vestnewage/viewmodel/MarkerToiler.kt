package ru.neosvet.vestnewage.viewmodel

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import androidx.work.Data
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.CheckItem
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.MarkerScreen
import ru.neosvet.vestnewage.helper.MarkerHelper
import ru.neosvet.vestnewage.loader.page.PageLoader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.OnlineObserver
import ru.neosvet.vestnewage.storage.DataBase
import ru.neosvet.vestnewage.storage.MarkersStorage
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.viewmodel.basic.MarkerStrings
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.MarkerState

class MarkerToiler : NeoToiler() {
    enum class Type {
        NONE, OPEN_PAGE, OPEN_COLS, ADD_COL, NEW_MARKER,
        OPEN_MARKER, GET_MARKER, ADD_MARKER, UPDATE_MARKER
    }

    private val storage = MarkersStorage()
    private lateinit var strings: MarkerStrings
    private var title = ""
    private var link = ""
    private var des = ""
    private var id = -1
    private var isPar = true //else - isPos
    private var task = Type.NONE
    private var sel: String = ""
    private var cols: String = ""
    private var pos = 0f
    private var arguments: Intent? = null
    private var posText: String = ""
        get() {
            if (field.isEmpty())
                field = helper.getPosText(pos)
            return field
        }
    private lateinit var helper: MarkerHelper

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, "Marker")
        .putString(Const.MODE, task.toString())
        .putString(Const.TITLE, "$id|$title")
        .putString(Const.LINK, link)
        .putString(Const.DESCRIPTION, des)
        .putString(Const.LIST, "sel{$sel}cols{$cols}posText{$posText}pos{$pos}")
        .build()

    override fun init(context: Context) {
        strings = MarkerStrings(
            selectedPosition = context.getString(R.string.sel_pos),
            selectedPar = context.getString(R.string.sel_par),
            selectedCollections = context.getString(R.string.sel_col),
            pageEntirely = context.getString(R.string.page_entirely),
            unusedDot = context.getString(R.string.unused_dot),
            titleAlreadyUsed = context.getString(R.string.title_already_used),
            noCollections = context.getString(R.string.no_collections),
            needSetCheck = context.getString(R.string.need_set_check)
        )
        helper = MarkerHelper(strings)
    }

    override suspend fun defaultState() {
        arguments?.let { intent ->
            link = intent.getStringExtra(Const.LINK) ?: ""
            id = intent.getIntExtra(DataBase.ID, -1)
            if (id == -1) {
                des = intent.getStringExtra(Const.DESCRIPTION) ?: ""
                isPar = true
                when {
                    intent.hasExtra(Const.PLACE) -> {
                        isPar = false
                        pos = intent.getFloatExtra(Const.PLACE, 0f)
                    }

                    intent.hasExtra(DataBase.PARAGRAPH) -> {
                        val par = intent.getIntExtra(DataBase.PARAGRAPH, 0)
                        sel = if (par == 0)
                            strings.pageEntirely
                        else par.toString()
                    }

                    intent.hasExtra(Const.PAGE) ->
                        sel = intent.getStringExtra(Const.PAGE) ?: strings.pageEntirely

                    else ->
                        sel = strings.pageEntirely

                }
            }
        }
        preOpen()
    }

    override fun onDestroy() {
        storage.close()
    }

    fun setArgument(intent: Intent) {
        arguments = intent
    }

    private fun preOpen() {
        task = Type.OPEN_MARKER
        scope.launch {
            openPage()
            if (title.isEmpty()) {
                postState(BasicState.NotLoaded)
                return@launch
            }
            openCols()
            if (id == -1) newMarker()
            else openMarker()
            postState(
                MarkerState.Primary(
                    helper = helper,
                    title = title,
                    des = des,
                    isPar = isPar,
                    sel = sel,
                    cols = cols
                )
            )
        }
    }

    private fun newMarker() = helper.run {
        task = Type.NEW_MARKER
        if (des.isEmpty()) {
            val date = DateUnit.initNow()
            des = date.toString()
        }
        cols = strings.selectedCollections + strings.noCollections
        colsList[0].isChecked = true
        when {
            isPar.not() ->
                updateSelPos()

            sel == strings.pageEntirely ->
                restoreParList()

            sel.contains(", ") -> {
                sel.split(", ").forEach { s ->
                    parsList[s.toInt()].isChecked = true
                }
                updateSelPar()
            }

            else -> {
                parsList[sel.toInt()].isChecked = true
                updateSelPar()
            }
        }
    }

    fun finish(des: String) {
        scope.launch {
            val row = getMarkerValues(des)
            if (id > -1) //edit helper
                updateMarker(id, row)
            else addMarker(row)
            postState(BasicState.Ready)
        }
    }

    private suspend fun openPage() {
        task = Type.OPEN_PAGE
        val storage = PageStorage()
        storage.open(link)
        if (OnlineObserver.isOnline.value && !storage.existsPage(link)) {
            postState(BasicState.Loading)
            val loader = PageLoader(NeoClient())
            loader.download(link, true)
        }
        val s = storage.getContentPage(link, false) ?: ""
        storage.close()
        helper.setContent(s)
        title = if (!s.contains(Const.NN)) ""
        else s.substring(0, s.indexOf(Const.NN))
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

    private fun openMarker() {
        task = Type.OPEN_MARKER
        var cursor = storage.getMarker(id.toString())
        cursor.moveToFirst()
        val iDes = cursor.getColumnIndex(Const.DESCRIPTION)
        des = cursor.getString(iDes) ?: ""
        val iPlace = cursor.getColumnIndex(Const.PLACE)
        var s = cursor.getString(iPlace) ?: ""
        setPlace(s)
        val iCols = cursor.getColumnIndex(DataBase.COLLECTIONS)
        s = cursor.getString(iCols)
        cursor.close()
        val b = StringBuilder(strings.selectedCollections)
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
        cols = b.toString()
        restoreColList()
    }

    private fun setPlace(s: String) {
        if (s.contains("%")) {
            isPar = false
            pos = s.substring(0, s.length - 1).replace(Const.COMMA, ".").toFloat()
            posText = helper.getPosText(pos)
            sel = strings.selectedPosition + s
        } else {
            isPar = true
            sel = if (s == "0") strings.pageEntirely
            else strings.selectedPar + s.replace(Const.COMMA, ", ")
            restoreParList()
        }
    }


    fun createCollection(title: String) {
        task = Type.ADD_COL
        scope.launch {
            helper.checkTitleCol(title)?.let {
                postState(BasicState.Message(it))
                return@launch
            }

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
                helper.colsList.add(
                    1,
                    CheckItem(
                        id = cursor.getInt(iId),
                        title = cursor.getString(iTitle),
                        isChecked = true
                    )
                )
            }
            cursor.close()
            cols += ", $title"
            postState(
                MarkerState.Text(
                    type = MarkerState.TextType.COL,
                    text = cols
                )
            )
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
        row.put(Const.DESCRIPTION, des)
        //определяем место
        var s = sel
        s = if (s.contains(":")) s.substring(s.indexOf(":") + 2)
            .replace(", ", Const.COMMA) else "0"
        row.put(Const.PLACE, s)
        //формулируем список подборок
        restoreColList()
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

    fun select(par: Boolean) {
        isPar = par
        if (isPar) updateSelPar()
        else updateSelPos()
        setState(
            MarkerState.Text(
                type = MarkerState.TextType.SEL,
                text = sel
            )
        )
    }

    fun savePosition(pos: Float) {
        this.pos = pos
        posText = helper.getPosText(pos)
        updateSelPos()
        setState(
            MarkerState.Text(
                type = MarkerState.TextType.SEL,
                text = sel
            )
        )
    }

    fun openCollection() {
        setState(
            MarkerState.Status(
                screen = MarkerScreen.COLLECTION,
                selection = sel
            )
        )
    }

    fun openParagraph() {
        setState(
            MarkerState.Status(
                screen = MarkerScreen.PARAGRAPH,
                selection = sel
            )
        )
    }

    fun openPosition() {
        setState(
            MarkerState.Status(
                screen = MarkerScreen.POSITION,
                selection = sel,
                positionText = helper.getPosText(pos),
                position = (pos * 10).toInt()
            )
        )
    }

    private fun updateSelPar() {
        val s = helper.getParString()
        sel = if (s == null) {
            helper.checkedAllPars()
            helper.getParString() ?: ""
        } else s
    }

    private fun updateSelPos() {
        sel = String.format(strings.selectedPosition + "%.1f%%", pos)
    }

    fun restoreParList() {
        if (sel.contains("№")) {
            helper.parsList.forEach { item ->
                item.isChecked = false
            }
            val s = sel.substring(sel.indexOf(":") + 2).replace(", ", Const.COMMA)
            s.split(Const.COMMA).forEach {
                helper.parsList[it.toInt()].isChecked = true
            }
        } else {
            helper.parsList.forEach { item ->
                item.isChecked = true
            }
        }
    }

    fun restoreColList() {
        val s = MarkersStorage.closeList(
            cols.substring(strings.selectedCollections.length).replace(", ", Const.COMMA)
        )
        var t: String
        helper.colsList.forEach { item ->
            t = MarkersStorage.closeList(item.title)
            item.isChecked = s.contains(t)
        }
    }

    fun saveParList() {
        val s = helper.getParString()
        if (s == null) {
            setState(BasicState.Message(strings.needSetCheck))
            return
        }
        sel = s
        setState(
            MarkerState.Text(
                type = MarkerState.TextType.SEL,
                text = sel
            )
        )
    }

    fun saveColList() {
        val s = helper.getColString()
        if (s == null) {
            setState(BasicState.Message(strings.needSetCheck))
            return
        }
        cols = s
        setState(
            MarkerState.Text(
                type = MarkerState.TextType.COL,
                text = cols
            )
        )
    }
}