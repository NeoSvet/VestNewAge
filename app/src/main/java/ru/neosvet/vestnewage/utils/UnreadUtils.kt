package ru.neosvet.vestnewage.utils

import android.content.ContentValues
import android.content.Context
import me.leolin.shortcutbadger.ShortcutBadger
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.storage.DevStorage
import ru.neosvet.vestnewage.storage.PageStorage

class UnreadUtils {
    companion object {
        const val NAME = "noread"
    }

    private lateinit var db: DataBase
    private val storage = PageStorage()
    private var time: Long = 0
    private var newIds: IntArray? = null
    private var isClosed = true

    private fun open() {
        if (!isClosed) return
        db = DataBase(NAME)
        isClosed = false
    }

    fun addLink(link: String, date: DateUnit): Boolean {
        open()
        var s = if (!link.contains(Const.HTML))
            link + Const.HTML else link
        storage.open(s)
        if (storage.existsPage(s)) return false // скаченную страницу игнорируем
        s = s.replace(Const.HTML, "")
        val cursor = db.query(
            table = NAME,
            column = Const.LINK,
            selection = Const.LINK + DataBase.Q,
            selectionArg = s
        )
        val exists = cursor.moveToFirst()
        cursor.close()
        if (exists) return true // уже есть в списке непрочитанного
        val row = ContentValues()
        row.put(Const.TIME, date.timeInMills)
        row.put(Const.LINK, s)
        db.insert(NAME, row)
        time = System.currentTimeMillis()
        return true
    }

    fun deleteLink(link: String) {
        open()
        val s = link.replace(Const.HTML, "")
        if (db.delete(NAME, Const.LINK + DataBase.Q, s) > 0) {
            time = System.currentTimeMillis()
            setBadge()
        }
        close()
    }

    val list: List<String>
        get() {
            open()
            val links: MutableList<String> = ArrayList()
            val cursor = db.query(
                table = NAME,
                orderBy = Const.TIME
            )
            if (cursor.moveToFirst()) {
                val iLink = cursor.getColumnIndex(Const.LINK)
                val iTime = cursor.getColumnIndex(Const.TIME)
                do {
                    if (cursor.getLong(iTime) > 0) links.add(cursor.getString(iLink))
                } while (cursor.moveToNext())
            }
            cursor.close()
            close()
            return links
        }

    val count: Int
        get() {
            open()
            val cursor = db.query(NAME)
            var k = 0
            if (cursor.moveToFirst()) k = cursor.count
            cursor.close()
            val storage = DevStorage()
            k += storage.unreadCount
            storage.close()
            close()
            return k
        }

    fun getNewId(k: Int): Int {
        if (newIds == null) newIds = intArrayOf(
            R.drawable.ic_0, R.drawable.ic_1, R.drawable.ic_2,
            R.drawable.ic_3, R.drawable.ic_4, R.drawable.ic_5, R.drawable.ic_6,
            R.drawable.ic_7, R.drawable.ic_8, R.drawable.ic_9
        )
        return if (k < newIds!!.size) newIds!![k] else R.drawable.ic_more
    }

    fun lastModified(): Long {
        val pref = App.context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return pref.getLong(Const.TIME, 0)
    }

    fun clearList() {
        open()
        db.delete(NAME)
        time = System.currentTimeMillis()
    }

    private fun close() {
        if (isClosed) return
        storage.close()
        db.close()
        isClosed = true
        if (time > 0) {
            val pref = App.context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            val editor = pref.edit()
            editor.putLong(Const.TIME, time)
            editor.apply()
        }
    }

    fun setBadge() {
        val storage = DevStorage()
        setBadge(storage.unreadCount)
        storage.close()
    }

    fun setBadge(countAds: Int) {
        open()
        val cursor = db.query(
            table = NAME,
            selection = Const.TIME + " > ?",
            selectionArg = "0"
        )
        val k = countAds + cursor.count
        if (k == 0) ShortcutBadger.removeCount(App.context)
        else ShortcutBadger.applyCount(App.context, k)
        cursor.close()
        close()
    }
}