package ru.neosvet.vestnewage.list.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import ru.neosvet.utils.Const
import ru.neosvet.vestnewage.list.ListItem
import ru.neosvet.vestnewage.model.basic.JournalStrings
import ru.neosvet.vestnewage.storage.JournalStorage

class JournalFactory(
    private val storage: JournalStorage,
    private val strings: JournalStrings,
    private val events: FactoryEvents
) : PagingSource<Int, ListItem>() {
    var total = 0
    var offset = 0
        private set

    override fun getRefreshKey(state: PagingState<Int, ListItem>): Int? {
        return null
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ListItem> {
        val position = params.key ?: offset
        offset = position
        events.startLoad()
        val list = storage.getList(position, strings)
        val next = position + Const.MAX_ON_PAGE
        events.finishLoad()
        return LoadResult.Page(
            data = list,
            prevKey = if (position == 0) null else position - Const.MAX_ON_PAGE,
            nextKey = if (next >= total) null else next
        )
    }
}