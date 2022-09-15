package ru.neosvet.vestnewage.view.list.paging

import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.loader.SummaryLoader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.storage.AdditionStorage

class AdditionFactory(
    private val storage: AdditionStorage,
    private val parent: NeoPaging
) : NeoPaging.Factory() {
    private val loader: SummaryLoader by lazy {
        SummaryLoader(NeoClient(NeoClient.Type.SECTION))
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
        return try {
            val list = openList(storage.max - position)
            val next = position + NeoPaging.ON_PAGE
            parent.finishPaging()
            LoadResult.Page(
                data = list,
                prevKey = if (position == 0) null else position - NeoPaging.ON_PAGE,
                nextKey = if (next >= storage.max) null else next
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