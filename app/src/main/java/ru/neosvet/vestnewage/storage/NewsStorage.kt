package ru.neosvet.vestnewage.storage

import android.annotation.SuppressLint
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.utils.Const

class NewsStorage : DataBase.Parent {
    companion object {
        const val NAME = "news"
    }

    private val db = DataBase(NAME, this)
    override fun createTable(db: SQLiteDatabase) {
        db.execSQL(
            DataBase.CREATE_TABLE + NAME + " ("
                    + DataBase.ID + " integer primary key,"
                    + Const.LINK + " text,"
                    + Const.TITLE + " text,"
                    + Const.DESCTRIPTION + " text,"
                    + Const.TIME + " integer);"
        )
        val row = ContentValues()
        row.put(DataBase.ID, 1)
        row.put(Const.TIME, 0)
        db.insert(NAME, null, row)
    }

    override fun close() =
        db.close()

    fun insert(row: ContentValues) =
        db.insert(NAME, row)

    @SuppressLint("Range")
    fun getItem(id: Int): BasicItem? {
        val cursor = db.query(
            table = NAME,
            selection = DataBase.ID + DataBase.Q,
            selectionArg = id.toString()
        )
        val item = if (cursor.moveToFirst()) {
            // val time = cursor.getLong(cursor.getColumnIndex(Const.TIME))
            val title = cursor.getString(cursor.getColumnIndex(Const.TITLE))
            val link = cursor.getString(cursor.getColumnIndex(Const.LINK))
            val d = cursor.getString(cursor.getColumnIndex(Const.DESCTRIPTION))
            var line: String? = null
            val item = BasicItem(title).apply { des = d }
            //item.des = fixDes(d)
            if (link.contains(Const.N)) {
                link.lines().forEach { s ->
                    if (line != null) line?.let {
                        item.addLink(s, it) //fixHead(s)
                        line = null
                    } else line = s
                }
            } else if (link.isNotEmpty())
                item.addLink(link)
            item
        } else null
        cursor.close()
        return item
    }

    fun getAll(): Cursor =
        db.query(table = NAME, orderBy = DataBase.ID)

    fun getTime(): Long {
        val cursor = db.query(
            table = NAME,
            selection = DataBase.ID + DataBase.Q,
            selectionArg = "1"
        )
        var time = 0L
        if (cursor.moveToFirst()) {
            val iTime = cursor.getColumnIndex(Const.TIME)
            time = cursor.getLong(iTime)
        }
        cursor.close()
        return time
    }

    fun insertTime() {
        val row = ContentValues()
        row.put(DataBase.ID, 1)
        row.put(Const.TIME, System.currentTimeMillis())
        db.insert(NAME, row)
    }

    fun clear() {
        db.delete(NAME)
    }
}