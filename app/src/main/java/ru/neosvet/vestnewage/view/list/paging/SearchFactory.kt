package ru.neosvet.vestnewage.view.list.paging

import androidx.paging.PagingState
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.storage.SearchStorage

class SearchFactory(
    private val parent: NeoPaging
) : NeoPaging.Factory() {
    private val storage = SearchStorage()
    var total = 0

    fun destroy() {
        storage.close()
    }

    override fun getRefreshKey(state: PagingState<Int, ListItem>): Int? {
        return null
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ListItem> {
        val position = params.key ?: offset
        if (position < offset)
            parent.newStartPosition(position)
        offset = position
        parent.startPaging()
        val list = storage.getList(offset)
        val next = position + NeoPaging.ON_PAGE
        parent.finishPaging()
        return LoadResult.Page(
            data = list,
            prevKey = if (position == 0) null else position - NeoPaging.ON_PAGE,
            nextKey = if (next >= total) null else next
        )
    }
}