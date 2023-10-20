package ru.neosvet.vestnewage.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import ru.neosvet.vestnewage.App
import java.io.Closeable

class DataBase(
    name: String,
    private val parent: Parent,
    write: Boolean = false
) : SQLiteOpenHelper(App.context, name, null, 1) {

    interface Parent : Closeable {
        fun createTable(db: SQLiteDatabase)
    }

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
        const val CREATE_TABLE = "create table if not exists "
        private val names: MutableSet<String> = LinkedHashSet()
        fun isBusy(name: String): Boolean = names.contains(name)
    }

    private var db: SQLiteDatabase = if (write) {
        if (names.contains(name)) throw NeoException.BaseIsBusy()
        names.add(name)
        this.writableDatabase
    } else this.readableDatabase

    override fun onCreate(db: SQLiteDatabase) {
        parent.createTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    @Synchronized
    override fun close() {
        if (!db.isReadOnly)
            names.remove(databaseName)
        db.close()
        super.close()
    }

    private fun checkWritable() {
        if (!db.isOpen || !db.isReadOnly) return
        if (names.contains(databaseName)) throw NeoException.BaseIsBusy()
        names.add(databaseName)
        db.close()
        db = this.writableDatabase
    }

    /*fun insert(table: String, nullColumnHack: String, row: ContentValues): Long {
        checkWritable()
        return if (db.isOpen)
            db.insert(table, nullColumnHack, row)
        else -1
    }*/

    fun insert(table: String, row: ContentValues): Long {
        checkWritable()
        return if (db.isOpen)
            db.insert(table, null, row)
        else -1
    }

    /*fun update(
        table: String,
        row: ContentValues,
        whereClause: String,
        whereArgs: Array<String>
    ): Int {
        checkWritable()
        return if (db.isOpen)
            db.update(table, row, whereClause, whereArgs)
        else -1
    }*/

    fun update(table: String, row: ContentValues, whereClause: String, whereArg: String): Int {
        checkWritable()
        return if (db.isOpen)
            db.update(table, row, whereClause, arrayOf(whereArg))
        else -1
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
        return if (db.isOpen)
            db.query(table, col, selection, args, groupBy, having, orderBy)
        else EmptyCursor()
    }

    fun rawQuery(query: String): Cursor {
        return if (db.isOpen)
            db.rawQuery(query, null)
        else EmptyCursor()
    }

    fun delete(table: String): Int {
        checkWritable()
        return if (db.isOpen)
            db.delete(table, null, null)
        else -1
    }

    fun delete(table: String, whereClause: String, whereArg: String): Int {
        checkWritable()
        return if (db.isOpen)
            db.delete(table, whereClause, arrayOf(whereArg))
        else -1
    }

    /*fun delete(table: String, whereClause: String, whereArgs: Array<String>): Int {
        checkWritable()
        return if (db.isOpen)
            db.delete(table, whereClause, whereArgs)
        else -1
    }*/
}