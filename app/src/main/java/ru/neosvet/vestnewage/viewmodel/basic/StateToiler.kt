package ru.neosvet.vestnewage.viewmodel.basic

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow

abstract class StateToiler : ViewModel() {
    private val cache = mutableListOf<NeoState>()
    private val mstate = MutableSharedFlow<NeoState>()
    val state = mstate.asSharedFlow()
    private var isReady = false

    fun notifyReady() {
        isReady = true
    }

    fun cacheState() = flow {
        isReady = false
        cache.forEach {
            if (it != NeoState.None)
                emit(it)
        }
        isReady = true
    }

    fun clearAllStates() {
        cache.clear()
    }

    fun clearSecondaryStates() {
        while (cache.size > 2)
            cache.removeAt(2)
    }

    protected suspend fun postState(state: NeoState) {
        addToCache(state)
        while (isReady.not())
            delay(25)
        isReady = false
        mstate.emit(state)
    }

    private fun addToCache(state: NeoState) {
        when (state) {
            //Primary states:
            is NeoState.ListValue, is NeoState.Calendar, is NeoState.Book, is NeoState.ListState ->
                setCache(0, state)
            is NeoState.LongValue ->
                setCache(1, state)
            //Secondary states:
            NeoState.Ready, NeoState.Success, NeoState.NoConnected ->
                setCache(2, state)
            is NeoState.Message, is NeoState.Error, is NeoState.Rnd ->
                setCache(3, state)
            else -> {}
        }
    }

    private fun setCache(i: Int, state: NeoState) {
        while (i > cache.size)
            cache.add(NeoState.None)
        if (i == cache.size)
            cache.add(state)
        else
            cache[i] = state
    }
}