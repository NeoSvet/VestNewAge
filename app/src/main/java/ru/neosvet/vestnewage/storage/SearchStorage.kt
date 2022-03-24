package ru.neosvet.vestnewage.storage

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import ru.neosvet.utils.Const
import ru.neosvet.utils.DataBase

/**
 * Created by NeoSvet on 24.03.2022.
 */

class SearchStorage(context: Context) {
    private val db = DataBase(context, Const.SEARCH)

    fun getResults(sortDesc: Boolean): Cursor = db.query(
        Const.SEARCH, null, null, null, null,
        null, DataBase.ID + if (sortDesc) DataBase.DESC else ""
    )

    fun update(id: String, row: ContentValues): Boolean =
        db.update(Const.SEARCH, row, DataBase.ID + DataBase.Q, id) > 0

    fun insert(row: ContentValues) =
        db.insert(Const.SEARCH, row)

    fun delete(id: String) =
        db.delete(Const.SEARCH, DataBase.ID + DataBase.Q, id)

    fun clear() =
        db.delete(Const.SEARCH)

    fun close() =
        db.close()
}