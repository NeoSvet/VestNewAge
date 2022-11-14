package ru.neosvet.vestnewage.data

import android.content.ContentResolver
import android.database.CharArrayBuffer
import android.database.ContentObserver
import android.database.Cursor
import android.database.DataSetObserver
import android.net.Uri
import android.os.Bundle

class EmptyCursor : Cursor {
    override fun close() {
    }

    override fun getCount(): Int = 0

    override fun getPosition(): Int = -1

    override fun move(offset: Int): Boolean = false

    override fun moveToPosition(position: Int): Boolean = false

    override fun moveToFirst(): Boolean = false

    override fun moveToLast(): Boolean = false

    override fun moveToNext(): Boolean = false

    override fun moveToPrevious(): Boolean = false

    override fun isFirst(): Boolean = false

    override fun isLast(): Boolean = false

    override fun isBeforeFirst(): Boolean = false

    override fun isAfterLast(): Boolean = false

    override fun getColumnIndex(columnName: String?): Int = -1

    override fun getColumnIndexOrThrow(columnName: String?): Int = 0

    override fun getColumnName(columnIndex: Int): String = ""

    override fun getColumnNames(): Array<String> = arrayOf()

    override fun getColumnCount(): Int = 0

    override fun getBlob(columnIndex: Int): ByteArray = byteArrayOf()

    override fun getString(columnIndex: Int): String = ""

    override fun copyStringToBuffer(columnIndex: Int, buffer: CharArrayBuffer?) {
    }

    override fun getShort(columnIndex: Int): Short = 0

    override fun getInt(columnIndex: Int): Int = 0

    override fun getLong(columnIndex: Int): Long = 0

    override fun getFloat(columnIndex: Int): Float = 0f

    override fun getDouble(columnIndex: Int): Double = 0.0

    override fun getType(columnIndex: Int): Int = Cursor.FIELD_TYPE_NULL

    override fun isNull(columnIndex: Int): Boolean = true

    @Deprecated("Deprecated in Java")
    override fun deactivate() {
    }

    @Deprecated("Deprecated in Java", ReplaceWith("false"))
    override fun requery(): Boolean = false

    override fun isClosed(): Boolean = true

    override fun registerContentObserver(observer: ContentObserver?) {
    }

    override fun unregisterContentObserver(observer: ContentObserver?) {
    }

    override fun registerDataSetObserver(observer: DataSetObserver?) {
    }

    override fun unregisterDataSetObserver(observer: DataSetObserver?) {
    }

    override fun setNotificationUri(cr: ContentResolver?, uri: Uri?) {
    }

    override fun getNotificationUri(): Uri = Uri.EMPTY

    override fun getWantsAllOnMoveCalls(): Boolean = false

    override fun setExtras(extras: Bundle?) {
    }

    override fun getExtras(): Bundle = Bundle.EMPTY

    override fun respond(extras: Bundle?): Bundle = Bundle.EMPTY
}