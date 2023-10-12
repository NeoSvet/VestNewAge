package ru.neosvet.vestnewage.storage

import android.annotation.SuppressLint
import android.content.ContentValues
import android.database.Cursor
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.utils.Const
import java.io.Closeable

class NewsStorage : Closeable {
    companion object {
        const val NAME = "ads"
    }

    private val db = DataBase(NAME)

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