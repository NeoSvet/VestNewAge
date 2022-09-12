package ru.neosvet.vestnewage.storage

import android.content.ContentValues
import android.database.Cursor
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.utils.Const
import java.io.Closeable

class AdditionStorage : Closeable {
    companion object {
        private const val LIMIT = " limit "
        private const val LINK = "href=\""
    }

    private lateinit var db: DataBase
    val name: String
        get() = db.databaseName
    private var isClosed = true
    var max = 0
        private set

    fun open() {
        if (isClosed.not()) return
        db = DataBase(DataBase.ADDITION)
        isClosed = false
    }

    fun insert(cv: ContentValues) =
        db.insert(DataBase.ADDITION, cv)

    fun update(id: Int, cv: ContentValues): Boolean =
        db.update(DataBase.ADDITION, cv, DataBase.ID + DataBase.Q, id.toString()) > 0

    fun delete(id: Int) =
        db.delete(DataBase.ADDITION, DataBase.ID + DataBase.Q, id.toString())

    private fun getCursor(offset: Int): Cursor = db.query(
        table = DataBase.ADDITION,
        groupBy = DataBase.ID,
        having = if (offset == 0) null else "${DataBase.ID} < ${offset + 1}",
        orderBy = DataBase.ID + DataBase.DESC + LIMIT + Const.MAX_ON_PAGE
    )

    fun getList(offset: Int): List<ListItem> {
        val list = mutableListOf<ListItem>()
        val cursor = getCursor(offset)
        if (cursor.moveToFirst()) {
            val iID = cursor.getColumnIndex(DataBase.ID)
            val iLink = cursor.getColumnIndex(Const.LINK)
            val iTitle = cursor.getColumnIndex(Const.TITLE)
            val iDes = cursor.getColumnIndex(Const.DESCTRIPTION)
            if (max == 0 && offset == 0)
                max = cursor.getInt(iID)
            do {
                val item = ListItem(cursor.getString(iTitle), cursor.getInt(iLink).toString())
                item.addHead(cursor.getInt(iID).toString())
                item.des = cursor.getString(iDes)
                if (item.des.contains(LINK))
                    addLinks(item.des, item)
                list.add(item)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    private fun addLinks(s: String, item: ListItem) {
        var i = s.indexOf(LINK)
        var n: Int
        while (i > -1) {
            i += LINK.length
            n = s.indexOf("\"", i)
            item.addLink(s.substring(i, n))
            i = s.indexOf(">", n) + 1
            n = s.indexOf("<", i)
            item.addHead(s.substring(i, n))
            i = s.indexOf(LINK, n)
        }
    }

    override fun close() {
        if (isClosed) return
        db.close()
        isClosed = true
    }

    fun findMax() {
        val cursor = db.query(
            table = DataBase.ADDITION,
            column = DataBase.ID,
            orderBy = DataBase.ID + DataBase.DESC + "${LIMIT}1"
        )
        max = if (cursor.moveToFirst())
            cursor.getInt(0)
        else 0
        cursor.close()
    }

    fun hasPost(id: Int): Boolean {
        val cursor = db.query(
            table = DataBase.ADDITION,
            column = DataBase.ID,
            selection = DataBase.ID + DataBase.Q,
            selectionArg = id.toString()
        )
        val result = cursor.moveToFirst()
        cursor.close()
        return result
    }
}