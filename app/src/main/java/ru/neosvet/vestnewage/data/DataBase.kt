package ru.neosvet.vestnewage.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.storage.AdsStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.UnreadUtils

class DataBase(name: String, write: Boolean = false) :
    SQLiteOpenHelper(App.context, name, null, 1) {
    companion object {
        const val PARAGRAPH = "par"
        const val JOURNAL = "journal"
        const val MARKERS = "markers"
        const val ADDITION = "addition"
        const val LIKE = " LIKE ?"
        const val GLOB = " GLOB ?"
        const val Q = " = ?"
        const val AND = " AND "
        const val ID = "id"
        const val DESC = " DESC"
        const val COLLECTIONS = "collections"
        const val ARTICLES = "00.00"
        const val DOCTRINE = "00.01"
        const val EMPTY_BASE_SIZE = 24576L
        private val names: MutableSet<String> = LinkedHashSet()
        fun isBusy(name: String): Boolean = names.contains(name)
    }

    private var db: SQLiteDatabase = if (write) {
        if (names.contains(name)) throw NeoException.BaseIsBusy()
        names.add(name)
        this.writableDatabase
    } else this.readableDatabase
    var isReadOnly = !write
        private set

    override fun onCreate(db: SQLiteDatabase) {
        if (databaseName.contains(".")) { // базы данных с материалами
            db.execSQL(
                "create table " + Const.TITLE + " ("
                        + ID + " integer primary key autoincrement," //id Const.TITLE
                        + Const.LINK + " text,"
                        + Const.TITLE + " text,"
                        + Const.TIME + " integer);"
            )
            //записываем дату создания (в дальнейшем это будет дата изменений):
            val row = ContentValues()
            row.put(Const.TIME, System.currentTimeMillis())
            db.insert(Const.TITLE, null, row)
            db.execSQL(
                "create table " + PARAGRAPH + " ("
                        + ID + " integer," //id Const.TITLE
                        + PARAGRAPH + " text);"
            )
            return
        }
        when (databaseName) {
            UnreadUtils.NAME -> db.execSQL(
                "create table if not exists " + UnreadUtils.NAME + " ("
                        + Const.LINK + " text primary key,"
                        + Const.TIME + " integer);"
            )
            AdsStorage.NAME -> db.execSQL(
                "create table if not exists " + AdsStorage.NAME + " ("
                        + Const.MODE + " integer,"
                        + Const.UNREAD + " integer default 1,"
                        + Const.TITLE + " text,"
                        + Const.LINK + " text,"
                        + Const.DESCTRIPTION + " text);"
            )
            Const.SEARCH -> db.execSQL(
                "create table if not exists " + Const.SEARCH + " ("
                        + Const.LINK + " text primary key,"
                        + Const.TITLE + " text,"
                        + ID + " integer," //number for sorting
                        + Const.DESCTRIPTION + " text);"
            )
            JOURNAL -> db.execSQL(
                "create table if not exists " + JOURNAL + " ("
                        + ID + " text primary key," // date&id Const.TITLE || date&id Const.TITLE&rnd_place
                        + Const.TIME + " integer);"
            )
            ADDITION -> db.execSQL(
                "create table if not exists " + ADDITION + " ("
                        + ID + " integer primary key," //number post on site
                        + Const.LINK + " integer," //number post in Telegram
                        + Const.TITLE + " text,"
                        + Const.DESCTRIPTION + " text);"
            )
            MARKERS -> {
                db.execSQL(
                    "create table " + MARKERS + " ("
                            + ID + " integer primary key autoincrement," //id закладки
                            + Const.LINK + " text," //ссылка на материал
                            + COLLECTIONS + " text," //список id подборок, в которые включен материал
                            + Const.DESCTRIPTION + " text," //описание
                            + Const.PLACE + " text);"
                ) //место в материале
                db.execSQL(
                    "create table " + COLLECTIONS + " ("
                            + ID + " integer primary key autoincrement," //id подборок
                            + MARKERS + " text," //список id закладок
                            + Const.PLACE + " integer," //место подборки в списке подоборок
                            + Const.TITLE + " text);"
                ) //название Подборки
                // добавляем подборку по умолчанию - "вне подборок":
                val row = ContentValues()
                row.put(Const.TITLE, App.context.getString(R.string.no_collections))
                db.insert(COLLECTIONS, null, row)
            }
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    @Synchronized
    override fun close() {
        db.close()
        if (isReadOnly.not()) {
            names.remove(databaseName)
            isReadOnly = true
        }
        super.close()
    }

    private fun checkWritable() {
        if (!isReadOnly) return
        if (names.contains(databaseName)) throw NeoException.BaseIsBusy()
        isReadOnly = false
        names.add(databaseName)
        db.close()
        db = this.writableDatabase
    }

    fun insert(table: String, nullColumnHack: String, row: ContentValues): Long {
        checkWritable()
        return db.insert(table, nullColumnHack, row)
    }

    fun insert(table: String, row: ContentValues): Long {
        checkWritable()
        return db.insert(table, null, row)
    }

    fun update(
        table: String,
        row: ContentValues,
        whereClause: String,
        whereArgs: Array<String>
    ): Int {
        checkWritable()
        return db.update(table, row, whereClause, whereArgs)
    }

    fun update(table: String, row: ContentValues, whereClause: String, whereArg: String): Int {
        checkWritable()
        return db.update(table, row, whereClause, arrayOf(whereArg))
    }

    fun query(
        table: String,
        column: String? = null,
        columns: Array<String>? = null,
        selection: String? = null,
        selectionArg: String? = null,
        selectionArgs: Array<String>? = null,
        groupBy: String? = null,
        having: String? = null,
        orderBy: String? = null
    ): Cursor {
        val args = if (selectionArg != null) arrayOf(selectionArg) else selectionArgs
        val col = if (column != null) arrayOf(column) else columns
        return db.query(table, col, selection, args, groupBy, having, orderBy)
    }

    fun rawQuery(query: String): Cursor {
        return db.rawQuery(query, null)
    }

    fun delete(table: String): Int {
        checkWritable()
        return db.delete(table, null, null)
    }

    fun delete(table: String, whereClause: String, whereArg: String): Int {
        checkWritable()
        return db.delete(table, whereClause, arrayOf(whereArg))
    }

    fun delete(table: String, whereClause: String, whereArgs: Array<String>): Int {
        checkWritable()
        return db.delete(table, whereClause, whereArgs)
    }
}