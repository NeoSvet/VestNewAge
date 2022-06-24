package ru.neosvet.vestnewage.utils

import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.StateToiler

class StateUtils(
    private val host: Host,
    private val toiler: StateToiler
) {
    interface Host {
        val scope: LifecycleCoroutineScope
        fun onChangedState(state: NeoState)
    }

    fun runObserve() {
        host.scope.launch {
            toiler.notifyReady()
            toiler.state.collect {
                host.onChangedState(it)
                toiler.notifyReady()
            }
        }
    }

    fun restore() {
        host.scope.launch {
            toiler.cacheState().collect {
                host.onChangedState(it)
            }
        }
    }
}