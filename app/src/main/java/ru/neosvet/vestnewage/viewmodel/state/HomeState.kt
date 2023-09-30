package ru.neosvet.vestnewage.viewmodel.state

import ru.neosvet.vestnewage.data.HomeItem
import ru.neosvet.vestnewage.data.Section

sealed class HomeState: NeoState {
    data class Primary(
        val isEditor: Boolean,
        val list: MutableList<HomeItem>,
        val menu: List<Section>
    ) : PrimaryState

    data class Status(
        val openedReader: Boolean
    ) : StatusState

    data class Loading(
        val index: Int
    ) : HomeState()
}