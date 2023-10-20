package ru.neosvet.vestnewage.storage

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import ru.neosvet.vestnewage.utils.Const

class DevStorage : DataBase.Parent {
    companion object {
        const val NAME = "dev"
        const val TYPE_TIME = 0
        const val TYPE_UPDATE = 1
        const val TYPE_ALL = 2
        const val TYPE_DES = 3
        const val TYPE_LINK = 4
    }

    private val db = DataBase(NAME, this)

    override fun createTable(db: SQLiteDatabase) {
        db.execSQL(
            DataBase.CREATE_TABLE + NAME + " ("
                    + Const.MODE + " integer,"
                    + Const.UNREAD + " integer default 1,"
                    + Const.TITLE + " text,"
                    + Const.LINK + " text,"
                    + Const.DESCTRIPTION + " text);"
        )
    }

    override fun close() =
        db.close()

    fun insert(row: ContentValues) =
        db.insert(NAME, row)

    fun getItem(title: String) = db.query(
        table = NAME,
        selection = Const.TITLE + DataBase.Q,
        selectionArg = title
    )

    fun getAll(): Cursor = db.query(NAME)

    fun getTime(): Long {
        val cursor = db.query(
            table = NAME,
            column = Const.TITLE,
            selection = Const.MODE + DataBase.Q,
            selectionArg = TYPE_TIME.toString()
        )
        val time = if (cursor.moveToFirst()) cursor.getString(0).toLong() else 0
        cursor.close()
        return time
    }

    fun clear() {
        db.delete(NAME)
    }

    val unreadCount: Int
        get() {
            val cursor = getUnread()
            val k = cursor.count
            cursor.close()
            return k
        }

    fun getUnread(): Cursor = db.query(
        table = NAME,
        selection = Const.UNREAD + DataBase.Q,
        selectionArg = "1"
    )

    private fun updateByTitle(title: String, row: ContentValues): Boolean =
        db.update(NAME, row, Const.TITLE + DataBase.Q, title) > 0

    private fun updateByDes(des: String, row: ContentValues): Boolean =
        db.update(NAME, row, Const.DESCTRIPTION + DataBase.Q, des) > 0

    fun newTime(): Long {
        val row = ContentValues()
        row.put(Const.MODE, TYPE_TIME)
        row.put(Const.UNREAD, 0)
        val time = System.currentTimeMillis()
        row.put(Const.TITLE, time.toString())
        if (db.update(NAME, row, Const.MODE + DataBase.Q, TYPE_TIME.toString()) < 1)
            insert(row)
        return time
    }

    fun setRead(title: String, des: String) {
        val row = ContentValues()
        row.put(Const.UNREAD, 0)
        if (!updateByTitle(title, row))
            updateByDes(des, row)
    }

    fun existsTitle(title: String): Boolean {
        val cursor = db.query(
            table = NAME,
            column = Const.TITLE,
            selection = Const.TITLE + DataBase.Q,
            selectionArg = title
        )
        val result = cursor.moveToFirst()
        cursor.close()
        return result
    }

    fun deleteItems(saveTitles: List<String>) {
        val cursor = getAll()
        if (!cursor.moveToFirst()) return
        val titles = mutableListOf<String>()
        val iTitle = cursor.getColumnIndex(Const.TITLE)
        while (cursor.moveToNext()) {
            val t = cursor.getString(iTitle)
            if (saveTitles.contains(t).not())
                titles.add(t)
        }
        cursor.close()
        titles.forEach {
            db.delete(NAME, Const.TITLE + DataBase.Q, it)
        }
    }
}