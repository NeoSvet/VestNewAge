package ru.neosvet.vestnewage.viewmodel.basic

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

abstract class StateToiler : ViewModel() {
    private var primaryState: NeoState? = null
    private var longValue: NeoState.LongValue? = null
    private var oneState: NeoState? = null
    private var twoState: NeoState? = null
    private val mstate = Channel<NeoState>()
    val state = mstate.receiveAsFlow()

    fun resume() {
        longValue?.let {
            mstate.trySend(it)
        }
    }

    fun cacheState() = flow {
        primaryState?.let { emit(it) }
        oneState?.let { emit(it) }
        twoState?.let { emit(it) }
    }

    fun clearLongValue() {
        longValue = null
    }

    fun clearStates() {
        oneState = null
        twoState = null
    }

    protected fun setState(state: NeoState) {
        addToCache(state)
        mstate.trySend(state)
    }

    protected suspend fun postState(state: NeoState) {
        addToCache(state)
        mstate.send(state)
    }

    private fun addToCache(state: NeoState) {
        when (state) {
            //Primary states:
            is NeoState.ListValue, is NeoState.Calendar, is NeoState.Book, is NeoState.ListState ->
                primaryState = state
            is NeoState.LongValue ->
                longValue = state
            //Secondary states:
            NeoState.Ready, NeoState.Success, NeoState.NoConnected ->
                oneState = state
            is NeoState.Message, is NeoState.Error, is NeoState.Rnd ->
                twoState = state
            else -> {}
        }
    }
}