package ru.neosvet.vestnewage.storage

import android.annotation.SuppressLint
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.StorageSearchable
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.date
import ru.neosvet.vestnewage.utils.dateFromLink
import ru.neosvet.vestnewage.utils.fromHTML
import ru.neosvet.vestnewage.utils.hasDate
import ru.neosvet.vestnewage.utils.isPoem
import java.util.regex.Pattern

class PageStorage : DataBase.Parent, StorageSearchable {
    companion object {
        @JvmStatic
        fun getDatePage(link: String): String {
            return when {
                link.contains(Const.DOCTRINE) -> DataBase.DOCTRINE
                link.contains(Const.HOLY_RUS) -> DataBase.HOLY_RUS
                !link.contains("/") || link.contains("press") -> DataBase.ARTICLES
                link.contains("pred") || link.hasDate ->
                    link.dateFromLink.my

                else -> {
                    if (link.contains("-")) { //https://blagayavest.info/poems/2017-03-08
                        DateUnit.parse(link.substring(link.lastIndexOf("/") + 1)).my
                    } else DataBase.ARTICLES
                }
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
    val isHolyRus: Boolean
        get() = name == DataBase.HOLY_RUS
    val isOtherBook: Boolean
        get() = isDoctrine || isHolyRus
    val isBook: Boolean
        get() = !isArticle && patternBook.matcher(name).matches()

    fun open(name: String, write: Boolean = false) {
        val n = when {
            name.contains(Const.HTML) -> getDatePage(name)
            name.contains(Const.DOCTRINE) -> DataBase.DOCTRINE
            name.contains(Const.HOLY_RUS) -> DataBase.HOLY_RUS
            else -> name
        }
        if (isClosed.not()) {
            if (n == this.name)
                return
            db.close()
        }
        db = DataBase(n, this, write)
        isClosed = false
        month = n.substring(0, 2).toInt()
        year = n.substring(3).toInt() + 2000
    }

    fun updateTime() {
        val cv = ContentValues()
        cv.put(Const.TIME, System.currentTimeMillis())
        if (db.update(Const.TITLE, cv, DataBase.ID + DataBase.Q, "1") == 0)
            insertTitle(cv)
    }

    fun updateTime(link: String, time: Long) {
        val cv = ContentValues()
        cv.put(Const.TIME, time)
        if (db.update(Const.TITLE, cv, Const.LINK + DataBase.Q, link) == 0) {
            cv.put(Const.LINK, link)
            insertTitle(cv)
        }
    }

    fun getPageTitle(title: String, link: String): String {
        return if (isArticle || isOtherBook || !link.hasDate || title == link) {
            title
        } else {
            if (link.isPoem) {
                link.date + " " + App.context.getString(R.string.poem) + " " +
                        (if (!title.startsWith("№")) Const.KV_OPEN else "") +
                        title + Const.KV_CLOSE
            } else if (!title.hasDate) "${link.date} $title"
            else title
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
        } catch (_: Exception) {
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
        while (cursor.moveToNext()) {
            s = cursor.getString(iLink)
            if (s != link) {
                cursor.close()
                return s
            }
        }
        cursor.close()
        return null
    }

    private fun getCursor(isPoems: Boolean): Cursor? {
        val cursor = if (isOtherBook) db.query(
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
        if (isOtherBook)
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
                content.append(cursor.getString(0).fromHTML)
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
        return if (title.isEmpty()) link else title
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

    fun putTitle(title: String, link: String, time: Long): Int {
        val cv = ContentValues()
        cv.put(Const.TITLE, title)
        cv.put(Const.TIME, time)
        return if (updateTitle(link, cv)) getPageId(link)
        else {
            cv.put(Const.LINK, link)
            db.insert(Const.TITLE, cv).toInt()
        }
    }

    fun putTitle(title: String, link: String) {
        val cv = ContentValues()
        cv.put(Const.TITLE, title)
        if (!updateTitle(link, cv)) {
            cv.put(Const.LINK, link)
            cv.put(Const.TIME, 0)
            db.insert(Const.TITLE, cv)
        }
    }

    fun putLink(link: String) {
        val cursor = db.query(
            table = Const.TITLE,
            column = DataBase.ID,
            selection = Const.LINK + DataBase.Q,
            selectionArg = link
        )
        if (!cursor.moveToFirst()) {
            val cv = ContentValues()
            cv.put(Const.LINK, link)
            cv.put(Const.TIME, 0)
            db.insert(Const.TITLE, cv)
        }
        cursor.close()
    }

    private fun insertTitle(cv: ContentValues) =
        db.insert(Const.TITLE, cv)

    fun insertParagraph(pageId: Int, par: String) {
        val cv = ContentValues()
        cv.put(DataBase.ID, pageId)
        cv.put(DataBase.PARAGRAPH, par)
        db.insert(DataBase.PARAGRAPH, cv)
    }

    fun addParagraph(link: String, par: String) {
        val id = getPageId(link)
        if (id == -1) return
        val cursor = db.query(
            table = DataBase.PARAGRAPH,
            selection = DataBase.ID + DataBase.Q + " AND " + DataBase.PARAGRAPH + DataBase.Q,
            selectionArgs = arrayOf(id.toString(), par)
        )
        val needAdd = !cursor.moveToFirst()
        cursor.close()
        if (needAdd)
            insertParagraph(id, par)
    }

    private fun updateTitle(link: String, cv: ContentValues): Boolean =
        db.update(Const.TITLE, cv, Const.LINK + DataBase.Q, link) > 0

    private fun deleteTitle(pageId: Int) =
        db.delete(Const.TITLE, DataBase.ID + DataBase.Q, pageId.toString())

    fun deleteParagraphs(pageId: Int) =
        db.delete(DataBase.PARAGRAPH, DataBase.ID + DataBase.Q, pageId.toString())

    private fun getLinks(): Cursor = db.query(
        table = Const.TITLE,
        column = Const.LINK
    )

    fun getLinksList(): MutableList<String> {
        val list = mutableListOf<String>()
        val cursor = getLinks()
        if (cursor.moveToFirst()) {
            // пропускаем первую запись - там только дата изменения списка
            while (cursor.moveToNext())
                list.add(cursor.getString(0))
        }
        cursor.close()
        return list
    }

    fun getListAll(): Cursor = db.query(Const.TITLE)

    override fun searchWhere(from: String, link: String?, where: String): Cursor {
        val w = if (link == null) where
        else "${DataBase.ID}='${getPageId(link)}' AND (" + where + ")"
        return db.rawQuery("SELECT * FROM $from WHERE $w")
    }

    override fun searchParagraphs(link: String, operator: String, find: String): Cursor = db.query(
        table = DataBase.PARAGRAPH,
        selection = DataBase.ID + DataBase.Q + " AND " + DataBase.PARAGRAPH + operator,
        selectionArgs = arrayOf(getPageId(link).toString(), find)
    )

    override fun searchParagraphs(operator: String, find: String): Cursor = db.query(
        table = DataBase.PARAGRAPH,
        selection = DataBase.PARAGRAPH + operator,
        selectionArg = find
    )

    override fun searchTitle(link: String, operator: String, find: String): Cursor = db.query(
        table = Const.TITLE,
        selection = Const.LINK + DataBase.Q + " AND " + Const.TITLE + operator,
        selectionArgs = arrayOf(link, find)
    )

    override fun searchTitle(operator: String, find: String): Cursor = db.query(
        table = Const.TITLE,
        selection = Const.TITLE + operator,
        selectionArg = find
    )

    override fun searchLink(find: String): Cursor = db.query(
        table = Const.TITLE,
        selection = Const.LINK + DataBase.LIKE,
        selectionArg = "%$find%"
    )

    override fun createTable(db: SQLiteDatabase) {
        db.execSQL(
            DataBase.CREATE_TABLE + Const.TITLE + " ("
                    + DataBase.ID + " integer primary key autoincrement," //id Const.TITLE
                    + Const.LINK + " text,"
                    + Const.TITLE + " text,"
                    + Const.TIME + " integer);"
        )
        //записываем дату создания (в дальнейшем это будет дата изменений):
        val row = ContentValues()
        row.put(Const.TIME, 0)
        db.insert(Const.TITLE, null, row)
        db.execSQL(
            DataBase.CREATE_TABLE + DataBase.PARAGRAPH + " ("
                    + DataBase.ID + " integer," //id Const.TITLE
                    + DataBase.PARAGRAPH + " text);"
        )
    }

    override fun close() {
        if (isClosed) return
        db.close()
        isClosed = true
    }

    fun deletePages(links: List<String>) {
        links.forEach {
            deletePage(it)
        }
    }

    fun deletePage(link: String) {
        val id = getPageId(link)
        deleteTitle(id)
        deleteParagraphs(id)
    }

    fun deleteParagraph(link: String, par: String) {
        val id = getPageId(link)
        if (id == -1) return
        db.delete(
            table = DataBase.PARAGRAPH,
            whereClause = DataBase.ID + DataBase.Q + " AND " + DataBase.PARAGRAPH + DataBase.Q,
            whereArgs = arrayOf(id.toString(), par)
        )
    }

    fun updateParagraph(link: String, oldPar: String, newPar: String) {
        val id = getPageId(link)
        if (id == -1) return
        val cv = ContentValues()
        cv.put(DataBase.ID, id)
        cv.put(DataBase.PARAGRAPH, newPar)
        db.update(
            table = DataBase.PARAGRAPH,
            row = cv,
            whereClause = DataBase.ID + DataBase.Q + " AND " + DataBase.PARAGRAPH + DataBase.Q,
            whereArgs = arrayOf(id.toString(), oldPar)
        )
    }

    fun changeLink(oldLink: String, newLink: String) {
        if (getPageId(oldLink) == -1) return
        val cv = ContentValues()
        cv.put(Const.LINK, newLink)
        db.update(
            table = Const.TITLE,
            row = cv,
            whereClause = Const.LINK + DataBase.Q,
            whereArgs = arrayOf(oldLink)
        )
    }
}