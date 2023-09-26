package ru.neosvet.vestnewage.viewmodel.state

import android.graphics.Point
import ru.neosvet.vestnewage.data.CalendarItem

sealed class CalendarState: NeoState {
    data class Primary(
        val time: Long,
        val label: String,
        val selected: Point,
        val years: List<String>,
        val months: List<String>,
        val isUpdateUnread: Boolean,
        val list: List<CalendarItem>
    ) : PrimaryState

    data class Status(
        val shownDwnDialog: Boolean
    ) : StatusState
}
