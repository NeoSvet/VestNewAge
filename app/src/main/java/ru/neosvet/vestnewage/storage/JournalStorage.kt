package ru.neosvet.vestnewage.storage

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import ru.neosvet.utils.Const
import ru.neosvet.utils.DataBase

/**
 * Created by NeoSvet on 23.03.2022.
 */

class JournalStorage(context: Context) {
    private val db = DataBase(context, DataBase.JOURNAL)

    fun update(id: String, row: ContentValues): Boolean =
        db.update(DataBase.JOURNAL, row, DataBase.ID + DataBase.Q, id) > 0

    fun insert(row: ContentValues) =
        db.insert(DataBase.JOURNAL, row)

    fun getIds(): Cursor =
        db.query(DataBase.JOURNAL, arrayOf(DataBase.ID))

    fun getAll(): Cursor = db.query(
        DataBase.JOURNAL, null, null, null,
        null, null, Const.TIME + DataBase.DESC
    )

    fun delete(id: String) =
        db.delete(DataBase.JOURNAL, DataBase.ID + DataBase.Q, id)

    fun clear() =
        db.delete(DataBase.JOURNAL)

    fun close() =
        db.close()
}