package ru.neosvet.vestnewage.view.list.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.storage.SearchStorage
import ru.neosvet.vestnewage.utils.Const

class SearchFactory(
    private val storage: SearchStorage,
    private val events: FactoryEvents
) : PagingSource<Int, ListItem>() {
    companion object {
        var offset = 0
    }

    var total = 0

    override fun getRefreshKey(state: PagingState<Int, ListItem>): Int? {
        return null
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ListItem> {
        val position = params.key ?: offset
        offset = position
        events.startLoad()
        val list = storage.getList(offset)
        val next = position + Const.MAX_ON_PAGE
        events.finishLoad()
        return LoadResult.Page(
            data = list,
            prevKey = if (position == 0) null else position - Const.MAX_ON_PAGE,
            nextKey = if (next >= total) null else next
        )
    }
}