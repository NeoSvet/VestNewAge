package ru.neosvet.vestnewage.view.list.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.viewmodel.basic.JournalStrings
import ru.neosvet.vestnewage.storage.JournalStorage
import ru.neosvet.vestnewage.utils.Const

class JournalFactory(
    private val storage: JournalStorage,
    private val strings: JournalStrings,
    private val parent: NeoPaging
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
        parent.startPaging()
        val list = storage.getList(position, strings)
        val next = position + Const.MAX_ON_PAGE
        parent.finishPaging()
        return LoadResult.Page(
            data = list,
            prevKey = if (position == 0) null else position - Const.MAX_ON_PAGE,
            nextKey = if (next >= total) null else next
        )
    }
}