package ru.neosvet.vestnewage.view.list.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.storage.SearchStorage
import ru.neosvet.vestnewage.utils.Const

class SearchFactory(
    private val storage: SearchStorage,
    private val parent: NeoPaging
) : PagingSource<Int, ListItem>() {
    companion object {
        var min = 0
            private set
        var offset = 0
            private set(value) {
                if (value < min)
                    min = value
                field = value
            }

        fun reset(startPosition: Int) {
            min = startPosition
            offset = startPosition
        }
    }

    var total = 0

    override fun getRefreshKey(state: PagingState<Int, ListItem>): Int? {
        return null
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ListItem> {
        val position = params.key ?: offset
        offset = position
        parent.startPaging()
        val list = storage.getList(offset)
        val prev = position - Const.MAX_ON_PAGE
        val next = position + Const.MAX_ON_PAGE
        parent.finishPaging()
        return LoadResult.Page(
            data = list,
            prevKey = if (prev <= min) null else prev,
            nextKey = if (next >= total) null else next
        )
    }
}