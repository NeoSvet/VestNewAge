package ru.neosvet.vestnewage.view.list.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.loader.SummaryLoader
import ru.neosvet.vestnewage.storage.AdditionStorage
import ru.neosvet.vestnewage.utils.Const

class AdditionFactory(
    private val storage: AdditionStorage,
    private val parent: NeoPaging
) : PagingSource<Int, ListItem>() {
    private val loader: SummaryLoader by lazy {
        SummaryLoader()
    }
    var offset = storage.max

    override fun getRefreshKey(state: PagingState<Int, ListItem>): Int? {
        return null
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ListItem> {
        val position = params.key ?: offset
        offset = position
        parent.startPaging()
        return try {
            val list = openList(position)
            val prev = position + Const.MAX_ON_PAGE
            val next = position - Const.MAX_ON_PAGE
            parent.finishPaging()
            LoadResult.Page(
                data = list,
                prevKey = if (prev > storage.max) null else prev,
                nextKey = if (next < 1) null else next
            )
        } catch (exception: Exception) {
            exception.printStackTrace()
            parent.onError(exception)
            LoadResult.Error(exception)
        }
    }

    private suspend fun openList(position: Int): List<ListItem> =
        withContext(Dispatchers.IO) {
            val list = storage.getList(position)
            list.ifEmpty {
                loader.loadAddition(storage, position)
                storage.getList(position)
            }
        }
}