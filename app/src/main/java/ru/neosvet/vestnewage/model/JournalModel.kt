package ru.neosvet.vestnewage.model

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import androidx.work.Data
import ru.neosvet.utils.Const
import ru.neosvet.utils.DataBase
import ru.neosvet.utils.Lib
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.helpers.DateHelper
import ru.neosvet.vestnewage.list.ListItem
import ru.neosvet.vestnewage.model.basic.JournalStrings
import ru.neosvet.vestnewage.model.basic.NeoViewModel
import ru.neosvet.vestnewage.model.basic.Ready
import ru.neosvet.vestnewage.model.basic.SuccessList
import ru.neosvet.vestnewage.storage.JournalStorage
import ru.neosvet.vestnewage.storage.PageStorage

class JournalModel : NeoViewModel() {
    private val journal = JournalStorage()
    private var offset = 0
    private var finish = true
    val isCanPaging: Boolean
        get() = offset > 0 || !finish
    private lateinit var strings: JournalStrings
    private var isInit = false

    fun init(context: Context) {
        if (isInit) return
        isInit = true
        strings = JournalStrings(
            format_time_back = context.getString(R.string.format_time_back),
            rnd_kat = context.getString(R.string.rnd_kat),
            rnd_pos = context.getString(R.string.rnd_pos),
            rnd_stih = context.getString(R.string.rnd_stih)
        )
    }

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, DataBase.JOURNAL)
        .putInt(Const.FIRST, offset)
        .putBoolean(Const.END, finish)
        .build()

    override suspend fun doLoad() {
        openList()
    }

    override fun onDestroy() {
        journal.close()
    }

    @SuppressLint("Range")
    private fun openList() {
        val list = mutableListOf<ListItem>()
        val curJ = journal.getAll()
        if (!curJ.moveToFirst()) {
            curJ.close()
            mstate.postValue(SuccessList(list))
        }
        val storage = PageStorage()
        var cursor: Cursor
        val iTime = curJ.getColumnIndex(Const.TIME)
        val iID = curJ.getColumnIndex(DataBase.ID)
        var i = 0
        var s: String
        var id: Array<String>
        var item: ListItem
        val now = DateHelper.initNow()
        if (offset > 0) curJ.moveToPosition(offset)
        do {
            id = curJ.getString(iID).split(Const.AND).toTypedArray()
            storage.open(id[0])
            cursor = storage.getPageById(id[1])
            if (cursor.moveToFirst()) {
                s = cursor.getString(cursor.getColumnIndex(Const.LINK))
                val title = cursor.getString(cursor.getColumnIndex(Const.TITLE))
                item = ListItem(storage.getPageTitle(title, s), s)
                val t = curJ.getLong(iTime)
                val d = DateHelper.putMills(t)
                item.des = String.format(
                    strings.format_time_back,
                    now.getDiffDate(t), d
                )
                if (id.size == 3) { //случайные
                    if (id[2] == "-1") { //случайный катрен или послание
                        s = if (s.contains(Const.POEMS))
                            strings.rnd_kat
                        else
                            strings.rnd_pos
                    } else { //случаный стих
                        cursor.close()
                        cursor = storage.getParagraphs(id[1])
                        s = strings.rnd_stih
                        if (cursor.moveToPosition(id[2].toInt()))
                            s += ":" + Const.N + Lib.withOutTags(
                                cursor.getString(0)
                            )
                    }
                    item.des = item.des + Const.N + s
                }
                list.add(item)
                i++
            } else { //материал отсутствует в базе - удаляем запись о нём из журнала
                journal.delete(curJ.getString(iID))
            }
            cursor.close()
            storage.close()
        } while (curJ.moveToNext() && i < Const.MAX_ON_PAGE)
        finish = curJ.moveToNext().not()
        curJ.close()
        mstate.postValue(SuccessList(list))
    }

    fun clear() {
        journal.clear()
        journal.close()
        finish = true
        offset = 0
        mstate.postValue(Ready)
    }

    fun prevPage() {
        if (offset == 0)
            mstate.postValue(Ready)
        else {
            offset -= Const.MAX_ON_PAGE
            load()
        }
    }

    fun nextPage() {
        if (finish)
            mstate.postValue(Ready)
        else {
            offset += Const.MAX_ON_PAGE
            load()
        }
    }

    fun reset() {
        offset = 0
        finish = false
    }
}