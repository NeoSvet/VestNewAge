package ru.neosvet.vestnewage.storage

import android.annotation.SuppressLint
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.fromHTML
import ru.neosvet.vestnewage.view.list.BasicAdapter
import ru.neosvet.vestnewage.view.list.paging.NeoPaging

class AdditionStorage : DataBase.Parent {
    companion object {
        private const val LIMIT = " limit "
        private const val LINK = "href=\""
    }

    private val today = DateUnit.initToday()
    private val yesterday = DateUnit.initToday().apply { changeDay(-1) }
    private val weekDays: Array<String> by lazy {
        App.context.resources.getStringArray(R.array.post_days)
    }
    private lateinit var db: DataBase
    val name: String
        get() = db.databaseName
    private var isClosed = true
    var max = 0
        private set

    fun open() {
        if (isClosed.not()) return
        db = DataBase(DataBase.ADDITION, this)
        isClosed = false
    }

    fun insert(cv: ContentValues) =
        db.insert(DataBase.ADDITION, cv)

    fun update(id: Int, cv: ContentValues): Boolean =
        db.update(DataBase.ADDITION, cv, DataBase.ID + DataBase.Q, id.toString()) > 0

    fun delete(id: Int) =
        db.delete(DataBase.ADDITION, DataBase.ID + DataBase.Q, id.toString())

    private fun getCursor(offset: Int) = db.query(
        table = DataBase.ADDITION,
        groupBy = DataBase.ID,
        having = if (offset == 0) null
        else "${DataBase.ID} < ${offset + 1} AND ${DataBase.ID} > ${offset - NeoPaging.ON_PAGE}",
        orderBy = DataBase.ID + DataBase.DESC + LIMIT + NeoPaging.ON_PAGE
    )

    fun search(date: String) = db.query(
        table = DataBase.ADDITION,
        selection = Const.DESCRIPTION + DataBase.LIKE,
        selectionArg = "${date}%"
    )

    @SuppressLint("Range")
    fun getItem(id: String, withDate: Boolean): BasicItem? {
        val cursor = db.query(
            table = DataBase.ADDITION,
            selection = DataBase.ID + DataBase.Q,
            selectionArg = id
        )
        val item = if (cursor.moveToFirst()) {
            var date = cursor.getString(cursor.getColumnIndex(Const.TIME))
            val title = if (withDate) {
                date = date.substring(0, 10).replace(".20", ".")
                cursor.getString(cursor.getColumnIndex(Const.TITLE)) + " ($date)"
            } else cursor.getString(cursor.getColumnIndex(Const.TITLE))
            val link = cursor.getInt(cursor.getColumnIndex(Const.LINK)).toString()
            var d = cursor.getString(cursor.getColumnIndex(Const.DESCRIPTION))
            if (withDate && d.indexOf(date) == 0)
                d = d.substring(d.indexOf(Const.N) + 1)
            if (d.contains("<a")) {
                var i = d.indexOf("<a")
                var n: Int
                var u: Int
                var s: String
                while (i > -1) {
                    i += 9
                    n = d.indexOf(">", i) + 1
                    u = d.indexOf("</a", n)
                    s = d.substring(i, n - 2)
                    d = d.substring(0, i - 9) + d.substring(n, u) +
                            ": $s" + d.substring(u + 4)
                    i = d.indexOf("<a")
                }
            }
            if (d.contains("<")) d = d.fromHTML
            BasicItem(title, Urls.TelegramUrl + link).apply { des = d }
        } else null
        cursor.close()
        return item
    }

    fun getList(offset: Int): List<BasicItem> {
        val list = mutableListOf<BasicItem>()
        val cursor = getCursor(offset)
        if (cursor.moveToFirst()) {
            val iID = cursor.getColumnIndex(DataBase.ID)
            val iLink = cursor.getColumnIndex(Const.LINK)
            val iTitle = cursor.getColumnIndex(Const.TITLE)
            val iTime = cursor.getColumnIndex(Const.TIME)
            val iDes = cursor.getColumnIndex(Const.DESCRIPTION)
            if (max == 0 && offset == 0)
                max = cursor.getInt(iID)
            do {
                val item = BasicItem(cursor.getString(iTitle), cursor.getInt(iLink).toString())
                item.addHead(cursor.getInt(iID).toString())
                item.des = BasicAdapter.LABEL_SEPARATOR + getDate(cursor.getString(iTime)) +
                        BasicAdapter.LABEL_SEPARATOR + cursor.getString(iDes)
                if (item.des.contains(LINK))
                    addLinks(item.des, item)
                list.add(item)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun getLastDate(): String {
        val cursor = getCursor(0)
        val t = if (cursor.moveToFirst()) {
            val iTime = cursor.getColumnIndex(Const.TIME)
            if (iTime > -1)
                getDate(cursor.getString(iTime))
            else ""
        } else ""
        cursor.close()
        return t
    }

    private fun getDate(s: String): String {
        val date = DateUnit.parse(s)
        if (date.day == today.day && date.month == today.month
            && date.year == today.year
        ) return date.toTimeString()
        if (date.day == yesterday.day && date.month == yesterday.month
            && date.year == yesterday.year
        ) return date.toTimeString() + ", " + weekDays[0]
        if (date.year == today.year) {
            if (today.timeInDays - date.timeInDays < 8)
                return date.toTimeString() + ", " + weekDays[date.dayWeek]
            val t = date.toAlterString()
            return t.substring(0, t.length - 5)
        }
        return date.toAlterString()
    }

    private fun addLinks(s: String, item: BasicItem) {
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

    override fun createTable(db: SQLiteDatabase) {
        db.execSQL(
            DataBase.CREATE_TABLE + DataBase.ADDITION + " ("
                    + DataBase.ID + " integer primary key," //number post on site
                    + Const.LINK + " integer," //number post in Telegram
                    + Const.TITLE + " text,"
                    + Const.TIME + " text,"
                    + Const.DESCRIPTION + " text);"
        )
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

    fun getTime(position: Int): String {
        val offset = max - position
        val cursor = db.query(
            table = DataBase.ADDITION,
            groupBy = DataBase.ID,
            having = if (offset == 0) null
            else "${DataBase.ID} = $offset",
            orderBy = DataBase.ID + LIMIT + "1"
        )
        if (!cursor.moveToFirst()) {
            cursor.close()
            return ""
        }
        val iTime = cursor.getColumnIndex(Const.TIME)
        val time = getDate(cursor.getString(iTime))
        cursor.close()
        return time
    }
}