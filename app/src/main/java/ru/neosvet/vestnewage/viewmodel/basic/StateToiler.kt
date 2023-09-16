package ru.neosvet.vestnewage.viewmodel.basic

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.FirstState
import ru.neosvet.vestnewage.viewmodel.state.NeoState
import ru.neosvet.vestnewage.viewmodel.state.PrimaryState
import ru.neosvet.vestnewage.viewmodel.state.SecondState
import ru.neosvet.vestnewage.viewmodel.state.StatusState

abstract class StateToiler : ViewModel() {
    private var primaryState: PrimaryState? = null
    private var statusState: StatusState? = null
    private var firstState: FirstState? = null
    private var secondState: SecondState? = null
    private var timePost: Long = 0
    protected var isInit = false
    private var isWait = false
    private val stateChannel = Channel<NeoState>()
    val state = stateChannel.receiveAsFlow()

    abstract suspend fun defaultState()

    fun setStatus(status: StatusState) {
        statusState = status
    }

    protected suspend fun restoreState(isLoading: Boolean) {
        if (primaryState == null) {
            defaultState()
            statusState?.let { stateChannel.send(it) }
            return
        }
        isWait = true
        primaryState?.let { stateChannel.send(it) }
        statusState?.let { stateChannel.send(it) }
        firstState?.let { stateChannel.send(it) }
        secondState?.let { stateChannel.send(it) }
        if (isLoading)
            stateChannel.send(BasicState.Loading)
        isWait = false
    }

    fun clearStates() {
        firstState = null
        secondState = null
    }

    fun clearPrimaryState() {
        primaryState = null
    }

    protected fun setState(state: NeoState) {
        addToCache(state)
        stateChannel.trySend(state)
    }

    protected suspend fun postState(state: NeoState) {
        if (isInit) addToCache(state) //TODO need? if not isInit move to NeoToiler
        while (isWait) delay(100)
        val timeNow = System.currentTimeMillis()
        timePost = if (timePost > 0 && timeNow - timePost < 100) {
            delay(100) //TODO test need?
            System.currentTimeMillis()
        } else timeNow
        stateChannel.send(state)
    }

    private fun addToCache(state: NeoState) {
        when (state) {
            is BasicState.Progress, BasicState.Loading ->
                return //Skip

            is PrimaryState ->
                primaryState = state

            is FirstState ->
                firstState = state

            is BasicState.Error ->
                if (state.isNeedReport) secondState = state

            is SecondState ->
                secondState = state
        }
    }
}