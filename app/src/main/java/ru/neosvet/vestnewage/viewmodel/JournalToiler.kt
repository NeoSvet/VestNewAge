package ru.neosvet.vestnewage.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.work.Data
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.storage.JournalStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.view.list.paging.JournalFactory
import ru.neosvet.vestnewage.view.list.paging.NeoPaging
import ru.neosvet.vestnewage.viewmodel.basic.JournalStrings
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.ListState

class JournalToiler : NeoToiler(), NeoPaging.Parent {
    private val journal = JournalStorage()
    private lateinit var strings: JournalStrings
    private val paging = NeoPaging(this)
    override val factory: JournalFactory by lazy {
        JournalFactory(journal, strings, paging)
    }
    override val isBusy: Boolean
        get() = isRun
    val isLoading: Boolean
        get() = paging.isPaging
    var isEmpty = false

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, "journal")
        .build()

    override fun init(context: Context) {
        strings = JournalStrings(
            format_time_back = context.getString(R.string.format_time_back),
            rnd_poem = context.getString(R.string.rnd_poem),
            rnd_epistle = context.getString(R.string.rnd_epistle),
            rnd_verse = context.getString(R.string.rnd_verse)
        )
    }

    override suspend fun defaultState() {
        preparing()
    }

    override fun onCleared() {
        journal.close()
        super.onCleared()
    }

    fun preparing() {
        viewModelScope.launch {
            val cursor = journal.getAll()
            if (cursor.moveToFirst()) {
                factory.total = cursor.count
                postState(ListState.Paging(cursor.count))
            } else {
                isEmpty = true
                postState(BasicState.Empty)
            }
            cursor.close()
        }
    }

    fun clear() {
        journal.clear()
        journal.close()
        isEmpty = true
        setState(BasicState.Empty)
    }

    fun paging(page: Int, pager: NeoPaging.Pager): Flow<PagingData<BasicItem>> {
        paging.setPager(pager)
        return paging.run(page)
    }

    override val pagingScope: CoroutineScope
        get() = viewModelScope

    override suspend fun postFinish() {
        if (isEmpty.not())
            postState(BasicState.Success)
    }

    override fun postError(error: BasicState.Error) {
        setState(error)
    }
}