package ru.neosvet.vestnewage.viewmodel.state

import ru.neosvet.vestnewage.data.HomeItem
import ru.neosvet.vestnewage.data.MenuItem
import ru.neosvet.vestnewage.data.Section

sealed class HomeState: NeoState {
    data class Primary(
        val isEditor: Boolean,
        val list: MutableList<HomeItem>,
        val menu: MutableList<Section>
    ) : PrimaryState

    data class Status(
        val openedReader: Boolean
    ) : StatusState

    data class Menu(
        val list: List<MenuItem>
    ) : FirstState

    data class ChangeMenuItem(
        val index: Int,
        val section: Section
    ) : HomeState()

    data class Loading(
        val index: Int
    ) : HomeState()
}