package ru.neosvet.vestnewage.view.list.paging

import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.storage.SearchStorage

class SearchFactory(
    private val storage: SearchStorage,
    private val parent: NeoPaging
) : NeoPaging.Factory() {
    override fun getRefreshKey(state: PagingState<Int, BasicItem>): Int? {
        return null
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, BasicItem> {
        val position = params.key ?: offset
        if (position < offset)
            parent.newStartPosition(position)
        offset = position
        parent.startPaging()
        val list = withContext(Dispatchers.IO) {
            storage.getList(offset)
        }
        val next = position + NeoPaging.ON_PAGE
        parent.finishPaging()
        return LoadResult.Page(
            data = list,
            prevKey = if (position == 0) null else position - NeoPaging.ON_PAGE,
            nextKey = if (next >= total) null else next
        )
    }
}