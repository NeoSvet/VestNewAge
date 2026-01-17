package ru.neosvet.vestnewage.storage

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.utils.Const

class MarkersStorage : DataBase.Parent {
    companion object {
        fun closeList(s: String?): String {
            return if (s == null) ""
            else Const.COMMA + s + Const.COMMA
        }

        fun openList(list: String?): String {
            if (!list.isNullOrEmpty()) {
                var s = list.trimStart().trimEnd()
                if (s.lastIndexOf(Const.COMMA) == s.length - 1)
                    s = s.take(s.length - 1)
                if (s.indexOf(Const.COMMA) == 0)
                    s = s.substring(1)
                return s
            }
            return list ?: ""
        }

        fun getList(s: String): Array<String> {
            return if (s.contains(Const.COMMA))
                s.split(Const.COMMA).toTypedArray()
            else arrayOf(s)
        }
    }

    private val db = DataBase(DataBase.MARKERS, this)

    fun updateCollection(id: Int, cv: ContentValues): Boolean =
        db.update(DataBase.COLLECTIONS, cv, DataBase.ID + DataBase.Q, id.toString()) > 0

    fun updateCollectionByTitle(title: String, cv: ContentValues): Boolean =
        db.update(DataBase.COLLECTIONS, cv, Const.TITLE + DataBase.Q, title) > 0

    fun updateMarker(id: String, cv: ContentValues): Boolean =
        db.update(DataBase.MARKERS, cv, DataBase.ID + DataBase.Q, id) > 0

    fun insertMarker(cv: ContentValues) =
        db.insert(DataBase.MARKERS, cv)

    fun insertCollection(cv: ContentValues) =
        db.insert(DataBase.COLLECTIONS, cv)

    fun getCollections(sortBy: String): Cursor = db.query(
        table = DataBase.COLLECTIONS,
        orderBy = sortBy
    )

    fun getCollectionsTitle(): Cursor = db.query(
        table = DataBase.COLLECTIONS,
        columns = arrayOf(DataBase.ID, Const.TITLE),
        orderBy = Const.PLACE
    )

    fun getCollectionsPlace(): Cursor = db.query(
        table = DataBase.COLLECTIONS,
        columns = arrayOf(DataBase.ID, Const.PLACE)
    )

    fun getCollection(colId: String): Cursor = db.query(
        table = DataBase.COLLECTIONS,
        selection = DataBase.ID + DataBase.Q,
        selectionArg = colId,
        orderBy = Const.PLACE
    )

    private fun getCollectionId(title: String): Cursor = db.query(
        table = DataBase.COLLECTIONS,
        column = DataBase.ID,
        selection = Const.TITLE + DataBase.Q,
        selectionArg = title,
        orderBy = Const.PLACE
    )

    fun getCollectionByPlace(place: Int): Cursor = db.query(
        table = DataBase.COLLECTIONS,
        selection = Const.PLACE + DataBase.Q,
        selectionArg = place.toString()
    )

    fun getMarkersList(colId: String): Cursor = db.query(
        table = DataBase.COLLECTIONS,
        column = DataBase.MARKERS,
        selection = DataBase.ID + DataBase.Q,
        selectionArg = colId
    )

    fun getMarkers(): Cursor = db.query(
        table = DataBase.MARKERS,
        orderBy = DataBase.ID
    )

    fun getMarkersListByTitle(colTitle: String): Cursor = db.query(
        table = DataBase.COLLECTIONS,
        column = DataBase.MARKERS,
        selection = Const.TITLE + DataBase.Q,
        selectionArg = colTitle
    )

    fun getMarker(marId: String): Cursor = db.query(
        table = DataBase.MARKERS,
        selection = DataBase.ID + DataBase.Q,
        selectionArg = marId
    )

    fun getMarkerCollections(marId: String): Cursor = db.query(
        table = DataBase.MARKERS,
        column = DataBase.COLLECTIONS,
        selection = DataBase.ID + DataBase.Q,
        selectionArg = marId
    )

    fun deleteCollection(id: String, markersList: Array<String>, defColTitle: String) {
        if (markersList.isEmpty()) {
            db.delete(DataBase.COLLECTIONS, DataBase.ID + DataBase.Q, id)
            return
        }

        val defCol = getCollectionId(defColTitle)
        defCol.moveToFirst()
        val defId: Int = defCol.getInt(0) //id подборки "Вне подборок"
        defCol.close()

        var s: String
        val b = StringBuilder()
        for (item in markersList) { //перебираем список закладок.
            val cursor = getMarkerCollections(item)
            if (!cursor.moveToFirst()) continue
            s = closeList(cursor.getString(0)) //список подборок у закладки
            cursor.close()
            s = s.replace(closeList(id), Const.COMMA) //убираем удаляемую подборку
            if (s.length == 1) { //в списке не осталось подборок
                s = defId.toString() //указываем "Вне подборок"
                // добавляем в список на добавление в "Вне подборок":
                b.append(item)
                b.append(Const.COMMA)
            } else
                s = openList(s)
            //обновляем закладку:
            val cv = ContentValues()
            cv.put(DataBase.COLLECTIONS, s)
            updateMarker(item, cv)
        }

        //дополняем список "Вне подоборок"
        if (b.isNotEmpty()) {
            //получаем список закладок в "Вне подоборок":
            val cursor = getMarkersList(defId.toString())
            s = if (cursor.moveToFirst())
                cursor.getString(0) else ""
            cursor.close()
            //дополняем список:
            val cv = ContentValues()
            cv.put(DataBase.MARKERS, b.toString() + s)
            updateCollection(defId, cv)
        }

        db.delete(DataBase.COLLECTIONS, DataBase.ID + DataBase.Q, id)
    }

    fun deleteMarker(id: String) {
        val collections = getMarkerCollections(id)
        if (collections.moveToFirst()) {
            var s = closeList(collections.getString(0)) //список подборок у закладки
            for (item in getList(s)) { //перебираем список подборок
                val cursor = db.query(
                    table = DataBase.COLLECTIONS,
                    column = DataBase.MARKERS,
                    selection = DataBase.ID + DataBase.Q,
                    selectionArg = item
                )
                if (cursor.moveToFirst()) {
                    s = closeList(cursor.getString(0)) //список закладок у подборки
                    s = s.replace(closeList(id), Const.COMMA) //убираем удаляемую закладку
                    s = openList(s)
                    //обновляем подборку:
                    val cv = ContentValues()
                    cv.put(DataBase.MARKERS, s)
                    db.update(DataBase.COLLECTIONS, cv, DataBase.ID + DataBase.Q, item)
                }
                cursor.close()
            }
        }
        collections.close()
        db.delete(DataBase.MARKERS, DataBase.ID + DataBase.Q, id)
    }

    override fun createTable(db: SQLiteDatabase) {
        db.execSQL(
            DataBase.CREATE_TABLE + DataBase.MARKERS + " ("
                    + DataBase.ID + " integer primary key autoincrement," //id закладки
                    + Const.LINK + " text," //ссылка на материал
                    + DataBase.COLLECTIONS + " text," //список id подборок, в которые включен материал
                    + Const.DESCRIPTION + " text," //описание
                    + Const.PLACE + " text);"
        ) //место в материале
        db.execSQL(
            DataBase.CREATE_TABLE + DataBase.COLLECTIONS + " ("
                    + DataBase.ID + " integer primary key autoincrement," //id подборок
                    + DataBase.MARKERS + " text," //список id закладок
                    + Const.PLACE + " integer," //место подборки в списке подоборок
                    + Const.TITLE + " text);"
        ) //название Подборки
        // добавляем подборку по умолчанию - "вне подборок":
        val row = ContentValues()
        row.put(Const.TITLE, App.context.getString(R.string.no_collections))
        db.insert(DataBase.COLLECTIONS, null, row)
    }

    override fun close() =
        db.close()

    fun foundMarker(values: Array<String>): Int {
        val cursor = db.query(
            table = DataBase.MARKERS,
            column = DataBase.ID,
            selection = Const.PLACE + DataBase.Q + DataBase.AND + Const.LINK +
                    DataBase.Q + DataBase.AND + Const.DESCRIPTION + DataBase.Q,
            selectionArgs = values
        )
        val result = if (cursor.moveToFirst())
            cursor.getInt(0) else -1
        cursor.close()
        return result
    }

    fun changeLink(oldLink: String, newLink: String) {
        val cursor = db.query(
            table = DataBase.MARKERS,
            column = DataBase.ID,
            selection = Const.LINK + DataBase.Q,
            selectionArg = oldLink
        )
        val id = if (cursor.moveToFirst())
            cursor.getInt(0) else -1
        cursor.close()
        if (id == -1) return
        val cv = ContentValues()
        cv.put(Const.LINK, newLink)
        db.update(
            table = DataBase.MARKERS,
            row = cv,
            whereClause = Const.LINK + DataBase.Q,
            whereArgs = arrayOf(oldLink)
        )
    }
}