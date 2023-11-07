package ru.neosvet.vestnewage.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.work.Data
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    private val storage = JournalStorage()
    private lateinit var strings: JournalStrings
    private val paging = NeoPaging(this)
    val isLoading: Boolean
        get() = paging.isPaging
    var isEmpty = false
    private var jobTime: Job? = null

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, "journal")
        .build()

    override fun init(context: Context) {
        strings = JournalStrings(
            formatOpened = context.getString(R.string.format_opened),
            formatRnd = context.getString(R.string.format_rnd),
            back = context.getString(R.string.back),
            rndPoem = context.getString(R.string.rnd_poem),
            rndEpistle = context.getString(R.string.rnd_epistle),
            rndVerse = context.getString(R.string.rnd_verse)
        )
    }

    override suspend fun defaultState() {
        openList(0)
    }

    override fun onCleared() {
        storage.close()
        super.onCleared()
    }

    fun openList(tab: Int) {
        scope.launch {
            storage.filter = if (tab == 1) JournalStorage.Type.RND
            else JournalStorage.Type.OPENED
            val cursor = storage.getCursor()
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
        storage.clear()
        storage.close()
        isEmpty = true
        setState(BasicState.Empty)
    }

    fun paging(page: Int, pager: NeoPaging.Pager): Flow<PagingData<BasicItem>> {
        paging.setPager(pager)
        return paging.run(page)
    }

    //------begin    NeoPaging.Parent
    override val factory: JournalFactory by lazy {
        JournalFactory(storage, strings, paging)
    }
    override val isBusy: Boolean
        get() = isRun
    override val pagingScope: CoroutineScope
        get() = viewModelScope

    override suspend fun postFinish() {
        if (isEmpty.not())
            postState(BasicState.Ready)
    }

    override fun postError(error: BasicState.Error) {
        setState(error)
    }
//------end    NeoPaging.Parent

    fun getTimeOn(position: Int) {
        jobTime?.cancel()
        jobTime = scope.launch {
            val time = storage.getTimeBack(position)
            if (time.isEmpty()) postState(BasicState.Message(time))
            else postState(BasicState.Message(time + strings.back))
        }
    }
}