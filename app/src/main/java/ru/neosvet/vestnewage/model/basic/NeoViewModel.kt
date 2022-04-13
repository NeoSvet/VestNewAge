package ru.neosvet.vestnewage.model.basic

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.work.Data
import kotlinx.coroutines.*
import ru.neosvet.utils.ErrorUtils

abstract class NeoViewModel : ViewModel() {
    protected val mstate = MutableLiveData<NeoState>()
    val state: LiveData<NeoState>
        get() = mstate
    protected val scope = CoroutineScope(Dispatchers.IO
            + CoroutineExceptionHandler { _, throwable ->
        errorHandler(throwable)
    })
    protected var loadIfNeed = false
    var isRun: Boolean = false
        protected set

    override fun onCleared() {
        scope.cancel()
        onDestroy()
        super.onCleared()
    }

    private fun errorHandler(throwable: Throwable) {
        isRun = false
        if (loadIfNeed)
            load()
        else {
            if (throwable is Exception) {
                ErrorUtils.setData(getInputData())
                ErrorUtils.setError(throwable)
            }
            mstate.postValue(NeoState.Error(throwable))
        }
    }

    fun cancel() {
        isRun = false
    }

    fun load() {
        if (isRun) return
        isRun = true
        scope.launch {
            loadIfNeed = false
            mstate.postValue(NeoState.Loading)
            doLoad()
            isRun = false
        }
    }

    protected abstract suspend fun doLoad()

    protected abstract fun onDestroy()

    protected abstract fun getInputData(): Data
}