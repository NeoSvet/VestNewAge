package ru.neosvet.vestnewage.viewmodel.basic

import android.content.Context
import androidx.work.Data
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.data.NeoException
import ru.neosvet.vestnewage.loader.basic.Loader
import ru.neosvet.vestnewage.network.OnlineObserver
import ru.neosvet.vestnewage.utils.ErrorUtils
import ru.neosvet.vestnewage.viewmodel.state.BasicState

abstract class NeoToiler : StateToiler() {
    protected var scope = initScope()
    protected var loadIfNeed = false
    protected var currentLoader: Loader? = null
    protected var isRun: Boolean = false

    private fun initScope() = CoroutineScope(Dispatchers.IO
            + CoroutineExceptionHandler { _, throwable ->
        errorHandler(throwable)
    })
    protected abstract fun getInputData(): Data

    protected abstract fun init(context: Context)

    fun start(context: Context) {
        if (!isInit) {
            init(context)
            isInit = true
        }
        scope.launch { restoreState(isRun) }
    }

    override fun onCleared() {
        cancel()
        scope.cancel()
        onDestroy()
        super.onCleared()
    }

    private fun errorHandler(throwable: Throwable) {
        throwable.printStackTrace()
        currentLoader?.cancel()
        scope = initScope()
        isRun = false
        if (loadIfNeed && throwable !is NeoException.BaseIsBusy)
            load()
        else {
            val utils = ErrorUtils(throwable)
            if (utils.isNotSkip)
                setState(utils.getErrorState(getInputData()))
        }
    }

    open fun cancel() {
        currentLoader?.cancel()
        isRun = false
    }

    fun load() {
        if (isRun) return
        if (checkConnect().not()) return
        isRun = true
        scope.launch {
            loadIfNeed = false
            postState(BasicState.Loading)
            doLoad()
            isRun = false
        }
    }

    protected fun checkConnect(): Boolean {
        if (OnlineObserver.isOnline.value)
            return true
        setState(BasicState.NoConnected)
        return false
    }

    protected open suspend fun doLoad() {}

    protected suspend fun reLoad() {
        if (loadIfNeed && checkConnect()) {
            isRun = true
            postState(BasicState.Loading)
            loadIfNeed = false
            doLoad()
        }
        isRun = false
    }

    protected open fun onDestroy() {}

}