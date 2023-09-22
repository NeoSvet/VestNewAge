package ru.neosvet.vestnewage.viewmodel.state

import ru.neosvet.vestnewage.data.HomeItem

sealed class HomeState: NeoState {
    data class Primary(
        val list: List<HomeItem>
    ) : PrimaryState

    data class Status(
        val openedReader: Boolean
    ) : StatusState

    data class Loading(
        val index: Int
    ) : HomeState()
}