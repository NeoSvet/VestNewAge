package ru.neosvet.vestnewage.view.list.paging

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.utils.Const

class NeoPaging(
    private val parent: Parent,
    private val limit: Int = Const.MAX_ON_PAGE,
    private val distance: Int = 3
) {
    interface Parent {
        val factory: PagingSource<*, ListItem>
        val isRun: Boolean
        val pagingScope: CoroutineScope
        fun postFinish()
    }

    var isPaging = false
        private set

    fun run() = Pager(
        config = PagingConfig(
            pageSize = limit,
            prefetchDistance = distance
        ),
        pagingSourceFactory = { parent.factory }
    ).flow

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
}