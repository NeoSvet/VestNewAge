package ru.neosvet.vestnewage.viewmodel.basic

import androidx.work.Data
import kotlinx.coroutines.*
import ru.neosvet.vestnewage.data.BaseIsBusyException
import ru.neosvet.vestnewage.network.OnlineObserver
import ru.neosvet.vestnewage.utils.ErrorUtils

abstract class NeoToiler : StateToiler() {
    protected var scope = initScope()
    protected var loadIfNeed = false
    var isRun: Boolean = false
        protected set

    private fun initScope() = CoroutineScope(Dispatchers.IO
            + CoroutineExceptionHandler { _, throwable ->
        errorHandler(throwable)
    })

    override fun onCleared() {
        cancel()
        scope.cancel()
        onDestroy()
        super.onCleared()
    }

    private fun errorHandler(throwable: Throwable) {
        throwable.printStackTrace()
        scope = initScope()
        isRun = false
        if(throwable is BaseIsBusyException) {
            setState(NeoState.Error(throwable))
            return
        }
        if (loadIfNeed)
            load()
        else {
            ErrorUtils.setData(getInputData())
            setState(NeoState.Error(throwable))
        }
    }

    open fun cancel() {
        isRun = false
    }

    fun load() {
        if (isRun) return
        if (checkConnect().not()) return
        isRun = true
        scope.launch {
            loadIfNeed = false
            postState(NeoState.Loading)
            doLoad()
            isRun = false
        }
    }

    protected fun checkConnect(): Boolean {
        if (OnlineObserver.isOnline.value)
            return true
        setState(NeoState.NoConnected)
        return false
    }

    protected open suspend fun doLoad() {}

    protected suspend fun reLoad() {
        if (loadIfNeed && checkConnect()) {
            isRun = true
            postState(NeoState.Loading)
            loadIfNeed = false
            doLoad()
        }
        isRun = false
    }

    protected open fun onDestroy() {}

    protected abstract fun getInputData(): Data
}