package ru.neosvet.vestnewage.view.list.paging

import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.loader.AdditionLoader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.storage.AdditionStorage

class AdditionFactory(
    private val storage: AdditionStorage,
    private val parent: NeoPaging
) : NeoPaging.Factory() {
    private val loader: AdditionLoader by lazy {
        AdditionLoader(NeoClient(NeoClient.Type.SECTION))
    }

    override fun getRefreshKey(state: PagingState<Int, BasicItem>): Int? {
        return null
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, BasicItem> {
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

    private suspend fun openList(position: Int): List<BasicItem> =
        withContext(Dispatchers.IO) {
            val list = storage.getList(position)
            val max = if (position > NeoPaging.ON_PAGE) NeoPaging.ON_PAGE else position
            if (list.size < max) {
                loader.load(storage, position)
                storage.getList(position)
            } else list
        }
}