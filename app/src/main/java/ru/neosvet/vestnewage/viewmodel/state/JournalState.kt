package ru.neosvet.vestnewage.viewmodel.state

sealed class JournalState : NeoState {
    data class Status(
        val firstPosition: Int
    ) : StatusState
}
