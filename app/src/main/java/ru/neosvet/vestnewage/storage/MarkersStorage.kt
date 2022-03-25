package ru.neosvet.vestnewage.storage

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import ru.neosvet.utils.Const
import ru.neosvet.utils.DataBase
import ru.neosvet.vestnewage.App

/**
 * Created by NeoSvet on 23.03.2022.
 */

class MarkersStorage {
    private val db = DataBase(DataBase.MARKERS)

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
        DataBase.COLLECTIONS, null, null,
        null, null, null, sortBy
    )

    fun getCollectionsTitle(): Cursor = db.query(
        DataBase.COLLECTIONS,
        arrayOf(DataBase.ID, Const.TITLE),
        null, null, null, null, Const.PLACE
    )

    fun getCollectionsPlace(): Cursor = db.query(
        DataBase.COLLECTIONS,
        arrayOf(DataBase.ID, Const.PLACE)
    )

    fun getCollection(colId: String): Cursor = db.query(
        DataBase.COLLECTIONS, null,
        DataBase.ID + DataBase.Q, arrayOf(colId),
        null, null, Const.PLACE
    )

    fun getCollectionId(title: String): Cursor = db.query(
        DataBase.COLLECTIONS, arrayOf(DataBase.ID),
        Const.TITLE + DataBase.Q, arrayOf(title),
        null, null, Const.PLACE
    )

    fun getCollectionByPlace(place: Int): Cursor = db.query(
        DataBase.COLLECTIONS, null,
        Const.PLACE + DataBase.Q, arrayOf(place.toString())
    )

    fun getMarkersList(colId: String): Cursor = db.query(
        DataBase.COLLECTIONS, arrayOf(DataBase.MARKERS),
        DataBase.ID + DataBase.Q, colId
    )

    fun getMarkers(): Cursor = db.query(
        DataBase.MARKERS, null, null,
        null, null, null, DataBase.ID
    )

    fun getMarkersListByTitle(colTitle: String): Cursor = db.query(
        DataBase.COLLECTIONS, arrayOf(DataBase.MARKERS),
        Const.TITLE + DataBase.Q, colTitle
    )

    fun getMarker(marId: String): Cursor =
        db.query(DataBase.MARKERS, null, DataBase.ID + DataBase.Q, marId)

    fun getMarkerCollections(marId: String): Cursor = db.query(
        DataBase.MARKERS, arrayOf(DataBase.COLLECTIONS),
        DataBase.ID + DataBase.Q, marId
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
                    DataBase.COLLECTIONS,
                    arrayOf(DataBase.MARKERS),
                    DataBase.ID + DataBase.Q,
                    item
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

    fun close() =
        db.close()

    fun foundMarker(values: Array<String>): Int {
        val cursor = db.query(
            DataBase.MARKERS, arrayOf(DataBase.ID),
            Const.PLACE + DataBase.Q + DataBase.AND + Const.LINK +
                    DataBase.Q + DataBase.AND + Const.DESCTRIPTION + DataBase.Q,
            values
        )
        val result = if (cursor.moveToFirst())
            cursor.getInt(0) else -1
        cursor.close()
        return result
    }

    fun closeList(s: String?): String {
        return if (s == null) ""
        else Const.COMMA + s + Const.COMMA
    }

    fun openList(list: String?): String {
        if (list != null && list.isNotEmpty()) {
            var s = list.trimStart().trimEnd()
            if (s.lastIndexOf(Const.COMMA) == s.length - 1)
                s = s.substring(0, s.length - 1)
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