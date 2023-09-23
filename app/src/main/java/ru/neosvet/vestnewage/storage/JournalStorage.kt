package ru.neosvet.vestnewage.storage

import android.content.ContentValues
import android.database.Cursor
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.isPoem
import ru.neosvet.vestnewage.view.list.paging.NeoPaging
import ru.neosvet.vestnewage.viewmodel.basic.JournalStrings
import java.io.Closeable
import java.util.LinkedList

class JournalStorage : Closeable {
    companion object {
        private const val LIMIT = 150
    }

    private val db = DataBase(DataBase.JOURNAL)

    fun update(id: String, row: ContentValues): Boolean =
        db.update(DataBase.JOURNAL, row, DataBase.ID + DataBase.Q, id) > 0

    fun insert(row: ContentValues) =
        db.insert(DataBase.JOURNAL, row)

    fun getIds(): Cursor = db.query(
        table = DataBase.JOURNAL,
        column = DataBase.ID
    )

    fun getAll(): Cursor = db.query(
        table = DataBase.JOURNAL,
        orderBy = Const.TIME + DataBase.DESC
    )

    fun delete(id: String) =
        db.delete(DataBase.JOURNAL, DataBase.ID + DataBase.Q, id)

    fun clear() =
        db.delete(DataBase.JOURNAL)

    override fun close() =
        db.close()

    suspend fun getLastId(): String? {
        val cursor = db.query(
            table = DataBase.JOURNAL,
            column = DataBase.ID,
            orderBy = Const.TIME + DataBase.DESC + " limit 1"
        )
        return if (cursor.moveToFirst())
            cursor.getString(0)
        else null
    }

    suspend fun getList(offset: Int, strings: JournalStrings): List<BasicItem> {
        val list = mutableListOf<BasicItem>()
        val curJ = getAll()
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
                item.des = String.format(
                    strings.format_time_back,
                    DateUnit.getDiffDate(now, t), d
                )
                if (id.size == 3) { //случайные
                    if (id[2] == "-1") { //случайный катрен или послание
                        s = if (s.isPoem)
                            strings.rnd_poem
                        else
                            strings.rnd_epistle
                    } else { //случаный стих
                        cursor.close()
                        cursor = storage.getParagraphs(id[1])
                        s = strings.rnd_verse
                        if (cursor.moveToPosition(id[2].toInt()))
                            s += ":" + Const.N + Lib.withOutTags(
                                cursor.getString(0)
                            )
                    }
                    item.des = item.des + Const.N + s
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

    suspend fun checkLimit() {
        val cursor = getIds()
        var i = cursor.count
        cursor.moveToFirst()
        while (i > LIMIT) {
            delete(cursor.getString(0))
            cursor.moveToNext()
            i--
        }
        cursor.close()
    }

    fun getTimeBack(position: Int): String {
        val cursor = getAll()
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
}