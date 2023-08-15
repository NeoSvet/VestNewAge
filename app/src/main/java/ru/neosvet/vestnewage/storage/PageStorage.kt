package ru.neosvet.vestnewage.storage

import android.annotation.SuppressLint
import android.content.ContentValues
import android.database.Cursor
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.utils.*
import java.io.Closeable
import java.util.regex.Pattern

class PageStorage : Closeable {
    companion object {
        @JvmStatic
        fun getDatePage(link: String): String {
            return if (link.contains(Const.DOCTRINE))
                DataBase.DOCTRINE
            else if (!link.contains("/") || link.contains("press"))
                DataBase.ARTICLES
            else if (link.contains("pred")) {
                when {
                    link.contains("2004") -> "12.04"
                    link.contains("2009") -> "01.09"
                    else -> "08.04"
                }
            } else {
                var s = link
                if (s.contains("=")) { //http://blagayavest.info/poems/?date=11-3-2017
                    s = s.substring(s.indexOf("-") + 1)
                    if (s.length == 6) s = "0$s"
                    s = s.replace("-20", ".")
                } else if (s.contains("-")) { ///2005/01-02.08.05.html
                    s = s.substring(s.indexOf("-") + 4, s.lastIndexOf("."))
                } else { //http://blagayavest.info/poems/11.03.17.html
                    s = if (s.noHasDate) DataBase.ARTICLES else s.date.substring(3)
                }
                s
            }
        }
    }

    private lateinit var db: DataBase
    val name: String
        get() = db.databaseName
    private val patternBook = Pattern.compile("\\d{2}\\.\\d{2}")
    private var isClosed = true
    var month = 0
        private set
    var year = 0
        private set
    val isOldBook: Boolean
        get() = year in 2004..2015
    val isArticle: Boolean
        get() = name == DataBase.ARTICLES
    val isDoctrine: Boolean
        get() = name == DataBase.DOCTRINE
    val isBook: Boolean
        get() = !isArticle && patternBook.matcher(name).matches()

    fun open(name: String, write: Boolean = false) {
        val n = if (name.contains(Const.HTML))
            getDatePage(name)
        else if (name.contains(Const.DOCTRINE))
            DataBase.DOCTRINE
        else name
        if (isClosed.not()) {
            if (n == this.name)
                return
            db.close()
        }
        db = DataBase(n, write)
        isClosed = false
        month = n.substring(0, 2).toInt()
        year = n.substring(3).toInt() + 2000
    }

    fun updateTime() {
        val row = ContentValues()
        row.put(Const.TIME, System.currentTimeMillis())
        if (!updateTitle(1, row))
            insertTitle(row)
    }

    fun getPageTitle(title: String, link: String): String {
        return if (isArticle || isDoctrine || link.noHasDate || title == link) {
            title
        } else {
            val s = link.date
            if (link.isPoem) {
                s + " " + App.context.getString(R.string.poem) + " " + Const.KV_OPEN + title + Const.KV_CLOSE
            } else "$s $title"
        }
    }

    fun getPageId(link: String): Int {
        val cursor = db.query(
            table = Const.TITLE,
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

    fun existsPage(link: String): Boolean {
        var exists = false
        try {
            val curTitle = db.query(
                table = Const.TITLE,
                column = DataBase.ID,
                selection = Const.LINK + DataBase.Q,
                selectionArg = link
            )
            if (curTitle.moveToFirst()) {
                val curPar = db.query(
                    table = DataBase.PARAGRAPH,
                    selection = DataBase.ID + DataBase.Q,
                    selectionArg = curTitle.getInt(0).toString()
                )
                exists = curPar.moveToFirst()
                curPar.close()
            }
            curTitle.close()
        } catch (e: Exception) {
        }
        return exists
    }

    fun getList(isPoems: Boolean): Cursor = db.query(
        table = Const.TITLE,
        selection = if (isPoems) Const.LINK + DataBase.LIKE
        else Const.LINK + " NOT" + DataBase.LIKE,
        selectionArg = "%" + Const.POEMS + "%",
        orderBy = Const.LINK
    )

    fun getNextPage(link: String): String? {
        val cursor = getCursor(link.isPoem) ?: return null
        val iLink = cursor.getColumnIndex(Const.LINK)
        var s: String
        do {
            s = cursor.getString(iLink)
            if (s == link) break
        } while (cursor.moveToNext())
        if (cursor.moveToNext()) {
            s = cursor.getString(iLink)
            cursor.close()
            return s
        }
        cursor.close()
        return null
    }

    private fun getCursor(isPoems: Boolean): Cursor? {
        val cursor = if (isDoctrine) db.query(
            table = Const.TITLE,
            orderBy = DataBase.ID
        ) else if (isOldBook) getListAll()
        else getList(isPoems)
        if (!cursor.moveToFirst()) {
            cursor.close()
            return null
        }
        if (isOldBook && !cursor.moveToNext()) {
            cursor.close()
            return null
        }
        if (isDoctrine)
            cursor.moveToNext()
        return cursor
    }

    fun getPrevPage(link: String): String? {
        val cursor = getCursor(link.isPoem) ?: return null
        val iLink = cursor.getColumnIndex(Const.LINK)
        var s: String
        var p: String? = null
        do {
            s = cursor.getString(iLink)
            if (s == link) {
                cursor.close()
                return p
            }
            p = s
        } while (cursor.moveToNext())
        cursor.close()
        return null
    }

    @SuppressLint("Range")
    fun getContentPage(link: String, onlyTitle: Boolean): String? {
        var cursor = db.query(
            table = Const.TITLE,
            selection = Const.LINK + DataBase.Q,
            selectionArg = link
        )
        val id: Int
        val content = StringBuilder()
        id = if (cursor.moveToFirst()) {
            content.append(
                getPageTitle(
                    cursor.getString(cursor.getColumnIndex(Const.TITLE)),
                    link
                )
            )
            if (onlyTitle) {
                cursor.close()
                db.close()
                return content.toString()
            }
            content.append(Const.N)
            content.append(Const.N)
            cursor.getInt(cursor.getColumnIndex(DataBase.ID))
        } else { // страница не загружена...
            cursor.close()
            return null
        }
        cursor.close()
        cursor = db.query(
            table = DataBase.PARAGRAPH,
            column = DataBase.PARAGRAPH,
            selection = DataBase.ID + DataBase.Q,
            selectionArg = id.toString()
        )
        if (cursor.moveToFirst()) {
            do {
                content.append(Lib.withOutTags(cursor.getString(0)))
                content.append(Const.N)
                content.append(Const.N)
            } while (cursor.moveToNext())
        } else { // страница не загружена...
            cursor.close()
            return null
        }
        cursor.close()
        content.delete(content.length - 2, content.length)
        return content.toString()
    }

    fun getPage(link: String): Cursor = db.query(
        table = Const.TITLE,
        selection = Const.LINK + DataBase.Q,
        selectionArg = link
    )

    fun getPageById(id: String): Cursor = db.query(
        table = Const.TITLE,
        selection = DataBase.ID + DataBase.Q,
        selectionArg = id
    )

    fun getPageById(id: Int): Cursor = getPageById(id.toString())

    fun getTitle(link: String): String {
        val cursor = db.query(
            table = Const.TITLE,
            column = Const.TITLE,
            selection = Const.LINK + DataBase.Q,
            selectionArg = link
        )
        val title = if (cursor.moveToFirst())
            cursor.getString(0) else link
        cursor.close()
        return if (title.isEmpty()) link
        else getPageTitle(title, link)
    }

    fun getParagraphs(id: String): Cursor = db.query(
        table = DataBase.PARAGRAPH,
        column = DataBase.PARAGRAPH,
        selection = DataBase.ID + DataBase.Q,
        selectionArg = id
    )

    fun getParagraphs(id: Int): Cursor = getParagraphs(id.toString())

    fun getParagraphs(title: Cursor): Cursor {
        val i = title.getColumnIndex(DataBase.ID)
        return db.query(
            table = DataBase.PARAGRAPH,
            column = DataBase.PARAGRAPH,
            selection = DataBase.ID + DataBase.Q,
            selectionArg = title.getInt(i).toString()
        )
    }

    fun insertTitle(cv: ContentValues) =
        db.insert(Const.TITLE, cv)

    fun insertParagraph(cv: ContentValues) =
        db.insert(DataBase.PARAGRAPH, cv)

    fun updateTitle(id: Int, cv: ContentValues): Boolean =
        db.update(Const.TITLE, cv, DataBase.ID + DataBase.Q, id.toString()) > 0

    fun updateTitle(link: String, cv: ContentValues): Boolean =
        db.update(Const.TITLE, cv, Const.LINK + DataBase.Q, link) > 0

    fun deleteTitle(pageId: Int) =
        db.delete(Const.TITLE, DataBase.ID + DataBase.Q, pageId.toString())

    fun deleteParagraphs(pageId: Int) =
        db.delete(DataBase.PARAGRAPH, DataBase.ID + DataBase.Q, pageId.toString())

    fun getLinks(): Cursor = db.query(
        table = Const.TITLE,
        column = Const.LINK
    )

    fun getLinksList(): MutableList<String> {
        val list = mutableListOf<String>()
        val cursor = getLinks()
        if (cursor.moveToFirst()) {
            // пропускаем первую запись - там только дата изменения списка
            while (cursor.moveToNext()) {
                val link = cursor.getString(0)
                list.add(link)
            }
        }
        cursor.close()
        return list
    }

    fun getListAll(): Cursor = db.query(Const.TITLE)

    fun rawQuery(from: String, where: String): Cursor =
        db.rawQuery("SELECT * FROM $from WHERE $where")

    fun searchParagraphs(link: String, operator: String, find: String): Cursor = db.query(
        table = DataBase.PARAGRAPH,
        column = DataBase.PARAGRAPH,
        selection = DataBase.ID + DataBase.Q + " AND " + DataBase.PARAGRAPH + operator,
        selectionArgs = arrayOf(getPageId(link).toString(), find)
    )

    fun searchParagraphs(operator: String, find: String): Cursor = db.query(
        table = DataBase.PARAGRAPH,
        selection = DataBase.PARAGRAPH + operator,
        selectionArg = find
    )

    fun searchTitle(link: String, operator: String, find: String): Cursor = db.query(
        table = Const.TITLE,
        selection = Const.LINK + DataBase.Q + " AND " + Const.TITLE + operator,
        selectionArgs = arrayOf(link, find)
    )

    fun searchTitle(operator: String, find: String): Cursor = db.query(
        table = Const.TITLE,
        selection = Const.TITLE + operator,
        selectionArg = find
    )

    fun searchLink(find: String): Cursor = db.query(
        table = Const.TITLE,
        selection = Const.LINK + DataBase.LIKE,
        selectionArg = "%$find%"
    )

    override fun close() {
        if (isClosed) return
        db.close()
        isClosed = true
    }

    fun deletePages(list: List<String>) {
        list.forEach {
            val id = getPageId(it)
            deleteTitle(id)
            deleteParagraphs(id)
        }
    }

    fun replaceId(aId: Int, bId: Int) {
        val cv = ContentValues()
        cv.put(DataBase.ID, 1000)
        db.update(Const.TITLE, cv, DataBase.ID + DataBase.Q, aId.toString())
        db.update(DataBase.PARAGRAPH, cv, DataBase.ID + DataBase.Q, aId.toString())
        cv.put(DataBase.ID, aId)
        db.update(Const.TITLE, cv, DataBase.ID + DataBase.Q, bId.toString())
        db.update(DataBase.PARAGRAPH, cv, DataBase.ID + DataBase.Q, bId.toString())
        cv.put(DataBase.ID, bId)
        db.update(Const.TITLE, cv, DataBase.ID + DataBase.Q, "1000")
        db.update(DataBase.PARAGRAPH, cv, DataBase.ID + DataBase.Q, "1000")
    }

    fun changeId(aId: Int, bId: Int) {
        val cv = ContentValues()
        cv.put(DataBase.ID, bId)
        db.update(Const.TITLE, cv, DataBase.ID + DataBase.Q, aId.toString())
        db.update(DataBase.PARAGRAPH, cv, DataBase.ID + DataBase.Q, aId.toString())
    }
}