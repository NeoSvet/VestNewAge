package ru.neosvet.vestnewage.viewmodel.state

sealed class SummaryState : NeoState {
    data class Status(
        val selectedTab: Int,
        val firstPosition: Int
    ) : StatusState
}
