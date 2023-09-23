package ru.neosvet.vestnewage.view.list.paging

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.work.Data
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.ErrorUtils
import ru.neosvet.vestnewage.viewmodel.state.BasicState

class NeoPaging(
    private val parent: Parent,
    private val limit: Int = ON_PAGE,
    private val distance: Int = 3
) {
    companion object {
        const val ON_PAGE = 16
    }

    abstract class Factory : PagingSource<Int, BasicItem>() {
        internal var offset: Int = 0
        var total: Int = 0
    }

    interface Parent {
        val factory: Factory
        val isBusy: Boolean
        val pagingScope: CoroutineScope
        suspend fun postFinish()
        fun postError(error: BasicState.Error)
    }

    interface Pager {
        fun setPage(page: Int)
    }

    var isPaging = false
        private set
    private var pager: Pager? = null

    private var timeFinish = 0L

    fun setPager(pager: Pager) {
        this.pager = pager
    }

    fun run(page: Int): Flow<PagingData<BasicItem>> {
        val p = page * ON_PAGE
        if (p >= parent.factory.total)
            parent.factory.offset = parent.factory.total - 1
        else parent.factory.offset = p
        pager?.setPage(page)
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
        if (parent.isBusy) return
        val now = System.currentTimeMillis()
        if (now - timeFinish < 200) {
            isPaging = false
        } else parent.pagingScope.launch {
            delay(300)
            parent.postFinish()
            isPaging = false
        }
        timeFinish = now
    }

    private fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, "Paging")
        .putString("Parent", parent.javaClass.simpleName)
        .putInt("Offset", parent.factory.offset)
        .build()

    fun onError(error: Exception) {
        isPaging = false
        val utils = ErrorUtils(error)
        if (utils.isNotSkip)
            parent.postError(utils.getErrorState(getInputData()))
    }
}