package ru.neosvet.vestnewage.viewmodel.state

sealed class MainState : NeoState {
    data class FirstRun(
        val withSplash: Boolean
    ) : MainState()

    data class Ads(
        val hasNew: Boolean,
        val warnIndex: Int,
        val timediff: Int
    ) : FirstState

    data class Status(
        val curSection: String,
        val isBlocked: Boolean,
        val isEditor: Boolean,
        val shownDwnDialog: Boolean,
        val actionIcon: Int
    ) : StatusState
}