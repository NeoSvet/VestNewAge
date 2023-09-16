package ru.neosvet.vestnewage.viewmodel.state

sealed class SettingsState : NeoState {
    data class Status(
        val listVisible: List<Boolean>,
        val alarmVisible: Boolean
    ) : StatusState
}
