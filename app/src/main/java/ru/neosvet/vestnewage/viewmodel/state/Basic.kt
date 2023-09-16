package ru.neosvet.vestnewage.viewmodel.state

interface NeoState
interface PrimaryState: NeoState
interface StatusState: NeoState
interface FirstState: NeoState
interface SecondState: NeoState

sealed class BasicState : NeoState {
    data object Loading : BasicState()
    data object NoConnected : FirstState
    data object NotLoaded : BasicState()
    data object Empty : BasicState()
    data object Ready : FirstState
    data object Success : FirstState
    data class Progress(val percent: Int) : BasicState()
    data class Message(val message: String) : SecondState
    class Error(
        val message: String,
        val information: String
    ) : SecondState {
        val isNeedReport: Boolean
            get() = information.isNotEmpty()
    }
}