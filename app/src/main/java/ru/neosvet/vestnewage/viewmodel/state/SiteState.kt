package ru.neosvet.vestnewage.viewmodel.state

sealed class SiteState : NeoState {
    data class Status(
        val selectedTab: Int
    ) : StatusState
}
