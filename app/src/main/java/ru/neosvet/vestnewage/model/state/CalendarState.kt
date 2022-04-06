package ru.neosvet.vestnewage.model.state

import ru.neosvet.vestnewage.list.CalendarItem

sealed class CalendarState {
    object Loading : CalendarState()
    object Finish : CalendarState()
    data class Result(
        val date: String,
        val prev: Boolean,
        val next: Boolean,
        val calendar: List<CalendarItem>
    ) : CalendarState()

    data class Progress(val percent: Int) : CalendarState()
    data class CheckTime(val sec: Int, val isCurMonth: Boolean) : CalendarState()
    data class Error(val throwable: Throwable) : CalendarState()
}
