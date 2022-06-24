package ru.neosvet.vestnewage.viewmodel.basic

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

abstract class StateToiler : ViewModel() {
    private val cache = mutableListOf<NeoState>()
    private val mstate = Channel<NeoState>()
    val state = mstate.receiveAsFlow()

    fun cacheState() = flow {
        cache.forEach {
            if (it != NeoState.None)
                emit(it)
        }
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
        mstate.send(state)
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