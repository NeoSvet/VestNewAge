package ru.neosvet.vestnewage.storage

import android.content.ContentValues
import android.database.Cursor
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.view.list.paging.NeoPaging
import java.io.Closeable

class SearchStorage : Closeable {
    private var db = DataBase(Const.SEARCH)
    var isDesc = false
    private var isClosed = false

    fun getResults(sortDesc: Boolean): Cursor = db.query(
        table = Const.SEARCH,
        orderBy = DataBase.ID + if (sortDesc) DataBase.DESC else ""
    )

    fun update(id: String, row: ContentValues): Boolean =
        db.update(Const.SEARCH, row, DataBase.ID + DataBase.Q, id) > 0

    fun insert(row: ContentValues) =
        db.insert(Const.SEARCH, row)

    fun delete(id: String) =
        db.delete(Const.SEARCH, DataBase.ID + DataBase.Q, id)

    fun deleteByLink(link: String) =
        db.delete(Const.SEARCH, Const.LINK + DataBase.Q, link)

    fun clear() =
        db.delete(Const.SEARCH)

    override fun close() {
        if (isClosed) return
        db.close()
        isClosed = true
    }

    fun open() {
        if (isClosed.not()) return
        db = DataBase(Const.SEARCH)
        isClosed = false
    }

    suspend fun getList(offset: Int): List<BasicItem> {
        val list = mutableListOf<BasicItem>()
        open()
        val cursor = getResults(isDesc)
        if (cursor.count == 0) {
            cursor.close()
            return list
        }
        if (!cursor.moveToPosition(offset))
            return list

        val iTitle = cursor.getColumnIndex(Const.TITLE)
        val iLink = cursor.getColumnIndex(Const.LINK)
        val iDes = cursor.getColumnIndex(Const.DESCTRIPTION)
        do {
            val item = BasicItem(cursor.getString(iTitle), cursor.getString(iLink))
            cursor.getString(iDes)?.let {
                item.des = it
            }
            list.add(item)
        } while (cursor.moveToNext() && list.size < NeoPaging.ON_PAGE)
        cursor.close()
        return list
    }

    fun getIdByLink(link: String): Int {
        val cursor: Cursor = db.query(
            table = Const.SEARCH,
            column = DataBase.ID,
            selection = Const.LINK + DataBase.Q,
            selectionArg = link
        )
        val r = if (cursor.moveToFirst())
            cursor.getInt(0)
        else -1
        cursor.close()
        return r
    }
}