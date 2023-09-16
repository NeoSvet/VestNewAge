package ru.neosvet.vestnewage.viewmodel.state

import ru.neosvet.vestnewage.data.CalendarItem
import ru.neosvet.vestnewage.data.DateUnit

sealed class CalendarState: NeoState {
    data class Primary(
        val time: Long,
        val date: DateUnit,
        val prev: Boolean,
        val next: Boolean,
        val isUpdateUnread: Boolean,
        val list: List<CalendarItem>
    ) : PrimaryState

    data class Status(
        val dateDialog: DateUnit?,
        val shownDwnDialog: Boolean
    ) : StatusState

    data class Finish(
        val prev: Boolean,
        val next: Boolean
    ) : CalendarState()

}
