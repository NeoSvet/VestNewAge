package ru.neosvet.vestnewage.storage

import android.content.ContentValues
import android.database.Cursor
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.utils.Const
import java.io.Closeable

class AdsStorage : Closeable {
    companion object {
        const val NAME = "devads"
        const val MODE_T: Byte = 0
        const val MODE_U: Byte = 1
        const val MODE_TLD: Byte = 2
        const val MODE_TD: Byte = 3
        const val MODE_TL: Byte = 4
    }

    private val db = DataBase(NAME)

    val unreadCount: Int
        get() {
            val cursor = getUnread()
            val k = cursor.count
            cursor.close()
            return k
        }

    fun insert(row: ContentValues) =
        db.insert(NAME, row)

    fun getAll(): Cursor = db.query(NAME)

    fun getUnread(): Cursor = db.query(
        table = NAME,
        selection = Const.UNREAD + DataBase.Q,
        selectionArg = "1"
    )

    fun getTime(): Cursor = db.query(
        table = NAME,
        column = Const.TITLE,
        selection = Const.MODE + DataBase.Q,
        selectionArg = MODE_T.toString()
    )

    fun updateByTitle(title: String, row: ContentValues): Boolean =
        db.update(NAME, row, Const.TITLE + DataBase.Q, title) > 0

    fun updateByDes(des: String, row: ContentValues): Boolean =
        db.update(NAME, row, Const.DESCTRIPTION + DataBase.Q, des) > 0

    fun newTime(): Long {
        val row = ContentValues()
        row.put(Const.MODE, MODE_T)
        row.put(Const.UNREAD, 0)
        val time = System.currentTimeMillis()
        row.put(Const.TITLE, time.toString())
        if (db.update(NAME, row, Const.MODE + DataBase.Q, MODE_T.toString()) < 1)
            insert(row)
        return time
    }

    fun setRead(item: ListItem) {
        val row = ContentValues()
        row.put(Const.UNREAD, 0)
        var t = item.title
        if (t.indexOf(App.context.getString(R.string.ad)) == 0)
            t = t.substring(t.indexOf(" ") + 1)
        if (!updateByTitle(t, row))
            updateByDes(item.head, row)
    }

    fun delete() =
        db.delete(NAME)

    fun clear() =
        db.delete(NAME, Const.MODE + " != ?", MODE_T.toString())

    override fun close() =
        db.close()

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