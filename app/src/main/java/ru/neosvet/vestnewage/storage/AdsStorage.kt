package ru.neosvet.vestnewage.storage

import android.content.ContentValues
import android.database.Cursor
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.utils.Const
import java.io.Closeable

class AdsStorage : DevAds, SiteAds {
    companion object {
        const val NAME = "ads"
        const val DEV_NAME = "devads"
        const val MODE_T: Byte = 0
        const val MODE_U: Byte = 1
        const val MODE_TLD: Byte = 2
        const val MODE_TD: Byte = 3
        const val MODE_TL: Byte = 4
    }

    private val db = DataBase(NAME)
    private var name = DEV_NAME
    val dev: DevAds
        get() {
            name = DEV_NAME
            return this
        }
    val site: SiteAds
        get() {
            name = NAME
            return this
        }

    override fun close() =
        db.close()

    override fun insert(row: ContentValues) =
        db.insert(name, row)

    override fun getAll(): Cursor = if (name == NAME)
        db.query(table = name, orderBy = DataBase.ID)
    else db.query(name)

    override fun getTime(): Long = if (name == NAME)
        getTimeSite() else getTimeDev()

    private fun getTimeSite(): Long {
        val cursor = db.query(
            table = name,
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

    private fun getTimeDev(): Long {
        val cursor = db.query(
            table = name,
            column = Const.TITLE,
            selection = Const.MODE + DataBase.Q,
            selectionArg = MODE_T.toString()
        )
        val time = if (cursor.moveToFirst()) cursor.getString(0).toLong() else 0
        cursor.close()
        return time
    }

    override fun insertTime() {
        val row = ContentValues()
        row.put(DataBase.ID, 1)
        row.put(Const.TIME, System.currentTimeMillis())
        db.insert(NAME, row)
    }

    override fun clear() {
        db.delete(name)
        //for Dev? db.delete(name, Const.MODE + " != ?", MODE_T.toString())
    }

    override val unreadCount: Int
        get() {
            val cursor = getUnread()
            val k = cursor.count
            cursor.close()
            return k
        }

    override fun getUnread(): Cursor = db.query(
        table = DEV_NAME,
        selection = Const.UNREAD + DataBase.Q,
        selectionArg = "1"
    )

    private fun updateByTitle(title: String, row: ContentValues): Boolean =
        db.update(DEV_NAME, row, Const.TITLE + DataBase.Q, title) > 0

    override fun updateByDes(des: String, row: ContentValues): Boolean =
        db.update(DEV_NAME, row, Const.DESCTRIPTION + DataBase.Q, des) > 0

    override fun newTime(): Long {
        val row = ContentValues()
        row.put(Const.MODE, MODE_T)
        row.put(Const.UNREAD, 0)
        val time = System.currentTimeMillis()
        row.put(Const.TITLE, time.toString())
        if (db.update(DEV_NAME, row, Const.MODE + DataBase.Q, MODE_T.toString()) < 1)
            insert(row)
        return time
    }

    override fun setRead(item: BasicItem) {
        val row = ContentValues()
        row.put(Const.UNREAD, 0)
        var t = item.title
        if (t.indexOf(App.context.getString(R.string.ad)) == 0)
            t = t.substring(t.indexOf(" ") + 1)
        if (!updateByTitle(t, row))
            updateByDes(item.head, row)
    }

    override fun existsTitle(title: String): Boolean {
        val cursor = db.query(
            table = DEV_NAME,
            column = Const.TITLE,
            selection = Const.TITLE + DataBase.Q,
            selectionArg = title
        )
        val result = cursor.moveToFirst()
        cursor.close()
        return result
    }

    override fun deleteItems(saveTitles: List<String>) {
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
            db.delete(DEV_NAME, Const.TITLE + DataBase.Q, it)
        }
    }
}

interface SiteAds : Closeable {
    fun insert(row: ContentValues): Long
    fun clear()
    fun getAll(): Cursor
    fun getTime(): Long
    fun insertTime()
}

interface DevAds : Closeable {
    val unreadCount: Int
    fun insert(row: ContentValues): Long
    fun getAll(): Cursor
    fun clear()
    fun getUnread(): Cursor
    fun getTime(): Long
    fun updateByDes(des: String, row: ContentValues): Boolean
    fun newTime(): Long
    fun setRead(item: BasicItem)
    fun existsTitle(title: String): Boolean
    fun deleteItems(saveTitles: List<String>)
}