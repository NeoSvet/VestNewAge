package ru.neosvet.vestnewage.model

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.neosvet.utils.Const
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.list.paging.FactoryEvents
import ru.neosvet.vestnewage.list.paging.JournalFactory
import ru.neosvet.vestnewage.model.basic.JournalStrings
import ru.neosvet.vestnewage.model.basic.NeoState
import ru.neosvet.vestnewage.model.basic.Ready
import ru.neosvet.vestnewage.model.basic.Success
import ru.neosvet.vestnewage.storage.JournalStorage

class JournalModel : ViewModel(), FactoryEvents {
    private val mstate = MutableLiveData<NeoState>()
    val state: LiveData<NeoState>
        get() = mstate
    private val journal = JournalStorage()
    private lateinit var strings: JournalStrings
    private val factory: JournalFactory by lazy {
        JournalFactory(journal, strings, this)
    }
    var loading = false
        private set
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

    fun paging() = Pager(
        config = PagingConfig(
            pageSize = Const.MAX_ON_PAGE,
            prefetchDistance = 3
        ),
        pagingSourceFactory = { factory }
    ).flow

    fun clear() {
        journal.clear()
        journal.close()
    }

    override fun startLoad() {
        loading = true
    }

    override fun finishLoad() {
        viewModelScope.launch {
            delay(300)
            mstate.postValue(Success)
            loading = false
        }
    }
}