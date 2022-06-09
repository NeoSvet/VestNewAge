package ru.neosvet.vestnewage.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.storage.JournalStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.view.list.paging.JournalFactory
import ru.neosvet.vestnewage.view.list.paging.NeoPaging
import ru.neosvet.vestnewage.viewmodel.basic.JournalStrings
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.basic.Ready
import ru.neosvet.vestnewage.viewmodel.basic.Success

class JournalToiler : NeoToiler(), NeoPaging.Parent {
    private val journal = JournalStorage()
    private lateinit var strings: JournalStrings
    private val paging = NeoPaging(this)
    override val factory: JournalFactory by lazy {
        JournalFactory(journal, strings, paging)
    }
    val isLoading: Boolean
        get() = paging.isPaging
    val offset: Int
        get() = factory.offset
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

    override fun onCleared() {
        journal.close()
        super.onCleared()
    }

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, "journal")
        .build()

    fun preparing() {
        viewModelScope.launch {
            val cursor = journal.getAll()
            if (cursor.moveToFirst())
                factory.total = cursor.count
            if (factory.total == 0)
                mstate.postValue(Ready)
            cursor.close()
        }
    }

    fun clear() {
        journal.clear()
        journal.close()
        mstate.postValue(Ready)
    }

    fun paging() = paging.run()

    override val pagingScope: CoroutineScope
        get() = viewModelScope

    override fun postFinish() {
        mstate.postValue(Success)
    }
}