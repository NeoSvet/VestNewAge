package ru.neosvet.vestnewage.storage

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.fromHTML
import ru.neosvet.vestnewage.utils.isPoem
import ru.neosvet.vestnewage.view.list.paging.NeoPaging
import ru.neosvet.vestnewage.viewmodel.basic.JournalStrings
import java.util.LinkedList

class JournalStorage : DataBase.Parent {
    companion object {
        private const val LIMIT = 208
        private const val FILTER = "%&%&%"
    }

    enum class Type {
        ALL, OPENED, RND
    }

    private val db = DataBase(DataBase.JOURNAL, this)
    var filter = Type.ALL

    fun update(id: String, row: ContentValues): Boolean =
        db.update(DataBase.JOURNAL, row, DataBase.ID + DataBase.Q, id) > 0

    fun insert(row: ContentValues) =
        db.insert(DataBase.JOURNAL, row)

    fun getCursor(): Cursor = when (filter) {
        Type.ALL -> db.query(
            table = DataBase.JOURNAL,
            orderBy = Const.TIME + DataBase.DESC
        )

        Type.OPENED -> db.query(
            table = DataBase.JOURNAL,
            selection = DataBase.ID + " NOT" + DataBase.LIKE,
            selectionArg = FILTER,
            orderBy = Const.TIME + DataBase.DESC
        )

        Type.RND -> db.query(
            table = DataBase.JOURNAL,
            selection = DataBase.ID + DataBase.LIKE,
            selectionArg = FILTER,
            orderBy = Const.TIME + DataBase.DESC
        )
    }

    fun delete(id: String) =
        db.delete(DataBase.JOURNAL, DataBase.ID + DataBase.Q, id)

    fun clear() =
        db.delete(DataBase.JOURNAL)

    override fun createTable(db: SQLiteDatabase) {
        db.execSQL(
            DataBase.CREATE_TABLE + DataBase.JOURNAL + " ("
                    + DataBase.ID + " text primary key," // date&id Const.TITLE || date&id Const.TITLE&rnd_place
                    + Const.PLACE + " real,"
                    + Const.TIME + " integer);"
        )
    }

    override fun close() =
        db.close()

    fun getLastId(): String? {
        val cursor = db.query(
            table = DataBase.JOURNAL,
            column = DataBase.ID,
            orderBy = Const.TIME + DataBase.DESC + " limit 1"
        )
        return if (cursor.moveToFirst())
            cursor.getString(0)
        else null
    }

    fun getList(offset: Int, strings: JournalStrings): List<BasicItem> {
        val list = mutableListOf<BasicItem>()
        val curJ = getCursor()
        if (!curJ.moveToFirst() || offset > curJ.count) {
            curJ.close()
            return list
        }
        if (offset > 0) curJ.moveToPosition(offset)
        val removeList = LinkedList<String>()
        val storage = PageStorage()
        var cursor: Cursor
        val iTime = curJ.getColumnIndex(Const.TIME)
        val iID = curJ.getColumnIndex(DataBase.ID)
        val iPlace = curJ.getColumnIndex(Const.PLACE)
        var i = 0
        var s: String
        val now = System.currentTimeMillis()
        do {
            val id = curJ.getString(iID).split(Const.AND)
            storage.open(id[0])
            cursor = storage.getPageById(id[1])
            if (cursor.moveToFirst()) {
                val iLink = cursor.getColumnIndex(Const.LINK)
                val iTitle = cursor.getColumnIndex(Const.TITLE)
                s = cursor.getString(iLink)
                val item = BasicItem(storage.getPageTitle(cursor.getString(iTitle), s), s)
                val t = curJ.getLong(iTime)
                val d = DateUnit.putMills(t)
                if (id.size == 2) {
                    val p = curJ.getFloat(iPlace)
                    item.des = String.format(
                        strings.format_opened,
                        DateUnit.getDiffDate(now, t), p, d
                    )
                } else { //случайные
                    if (id[2] == "-1") { //случайный катрен или послание
                        s = if (s.isPoem) strings.rnd_poem
                        else strings.rnd_epistle
                    } else { //случаный стих
                        cursor.close()
                        cursor = storage.getParagraphs(id[1])
                        s = strings.rnd_verse
                        if (cursor.moveToPosition(id[2].toInt()))
                            s += ":" + Const.N + cursor.getString(0).fromHTML
                    }
                    item.des = String.format(
                        strings.format_rnd,
                        DateUnit.getDiffDate(now, t), d, s
                    )
                }
                list.add(item)
                i++
            } else { //материал отсутствует в базе - удаляем запись о нём из журнала
                removeList.add(curJ.getString(iID))
            }
            cursor.close()
            storage.close()
        } while (curJ.moveToNext() && i < NeoPaging.ON_PAGE)
        curJ.close()
        removeList.forEach {
            delete(it)
        }
        return list
    }

    fun checkLimit() {
        val cursor = getCursor()
        var i = cursor.count
        cursor.moveToLast()
        val iID = cursor.getColumnIndex(DataBase.ID)
        while (i > LIMIT) {
            delete(cursor.getString(iID))
            cursor.moveToPrevious()
            i--
        }
        cursor.close()
    }

    fun getTimeBack(position: Int): String {
        val cursor = getCursor()
        if (!cursor.moveToFirst() || position > cursor.count) {
            cursor.close()
            return ""
        }
        cursor.moveToPosition(position)
        val iTime = cursor.getColumnIndex(Const.TIME)
        val t = cursor.getLong(iTime)
        cursor.close()
        return DateUnit.getDiffDate(System.currentTimeMillis(), t)
    }

    fun getPlace(id: String): Float {
        val cursor = db.query(
            table = DataBase.JOURNAL,
            selection = DataBase.ID + DataBase.Q,
            selectionArg = id
        )
        val r = if (cursor.moveToFirst()) {
            val i = cursor.getColumnIndex(Const.PLACE)
            cursor.getFloat(i)
        } else 0f
        cursor.close()
        return r
    }
}