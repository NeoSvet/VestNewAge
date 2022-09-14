package ru.neosvet.vestnewage.view.list.paging

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.data.ListItem

class NeoPaging(
    private val parent: Parent,
    private val limit: Int = ON_PAGE,
    private val distance: Int = 3
) {
    companion object {
        const val ON_PAGE = 15
    }

    abstract class Factory : PagingSource<Int, ListItem>() {
        internal var offset: Int = 0
        val page: Int
            get() = offset / ON_PAGE
    }

    interface Parent {
        val factory: Factory
        val isRun: Boolean
        val pagingScope: CoroutineScope
        suspend fun postFinish()
        fun postError(error: Exception)
    }

    interface Pager {
        fun setPage(page: Int)
    }

    var isPaging = false
        private set
    private var pager: Pager? = null

    fun setPager(pager: Pager) {
        this.pager = pager
    }

    fun run(page: Int): Flow<PagingData<ListItem>> {
        parent.factory.offset = page * ON_PAGE
        pager?.setPage(parent.factory.page)
        return Pager(
            config = PagingConfig(
                pageSize = limit,
                prefetchDistance = distance
            ),
            pagingSourceFactory = { parent.factory }
        ).flow
    }

    fun newStartPosition(position: Int) {
        pager?.setPage(position / ON_PAGE)
    }

    fun startPaging() {
        isPaging = true
    }

    fun finishPaging() {
        if (parent.isRun) return
        parent.pagingScope.launch {
            delay(300)
            parent.postFinish()
            isPaging = false
        }
    }

    fun onError(error: Exception) {
        parent.postError(error)
    }
}