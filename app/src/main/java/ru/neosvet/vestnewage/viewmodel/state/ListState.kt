package ru.neosvet.vestnewage.viewmodel.state

import ru.neosvet.vestnewage.data.BasicItem

sealed class ListState : NeoState {
    data class Primary(
        val time: Long = 0L,
        val list: List<BasicItem>
    ) : PrimaryState

    data class Paging(
        val max: Int
    ) : ListState()

    data class Remove(
        val index: Int
    ) : ListState()

    data class Move(
        val indexFrom: Int,
        val indexTo: Int
    ) : ListState()

    data class Update<T>(
        val index: Int,
        val item: T
    ) : ListState()
}
