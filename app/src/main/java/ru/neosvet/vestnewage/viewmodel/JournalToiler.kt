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
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler

class JournalToiler : NeoToiler(), NeoPaging.Parent {
    private val journal = JournalStorage()
    private lateinit var strings: JournalStrings
    private val paging = NeoPaging(this)
    override val factory: JournalFactory by lazy {
        JournalFactory(journal, strings, paging)
    }
    val isLoading: Boolean
        get() = paging.isPaging
    var isEmpty = false
    val offset: Int
        get() = factory.offset
    private var isInit = false

    fun init(context: Context) {
        if (isInit) return
        isInit = true
        strings = JournalStrings(
            format_time_back = context.getString(R.string.format_time_back),
            rnd_poem = context.getString(R.string.rnd_poem),
            rnd_epistle = context.getString(R.string.rnd_epistle),
            rnd_verse = context.getString(R.string.rnd_verse)
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
            if (factory.total == 0) {
                isEmpty = true
                postState(NeoState.Ready)
            }
            cursor.close()
        }
    }

    fun clear() {
        journal.clear()
        journal.close()
        isEmpty = true
        setState(NeoState.Ready)
    }

    fun paging() = paging.run()

    override val pagingScope: CoroutineScope
        get() = viewModelScope

    override suspend fun postFinish() {
        if (isEmpty.not())
            postState(NeoState.Success)
    }
}