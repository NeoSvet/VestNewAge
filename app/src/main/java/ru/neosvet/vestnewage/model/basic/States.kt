package ru.neosvet.vestnewage.model.basic

import ru.neosvet.vestnewage.list.CalendarItem
import ru.neosvet.vestnewage.list.ListItem

sealed class NeoState {
    object Loading : NeoState()
    data class Error(val throwable: Throwable) : NeoState()
}

data class CheckTime(val sec: Long) : NeoState()

data class ProgressState(val percent: Int) : NeoState()

data class MessageState(val message: String) : NeoState()

data class SuccessList(
    val list: List<ListItem>
) : NeoState()

data class SuccessCalendar(
    val date: String,
    val prev: Boolean,
    val next: Boolean,
    val list: List<CalendarItem>
) : NeoState()

data class SuccessBook(
    val date: String,
    val prev: Boolean,
    val next: Boolean,
    val list: List<ListItem>
) : NeoState()

data class SuccessPage(
    val url: String,
    val timeInSeconds: Long,
    val isOtkr: Boolean = false
) : NeoState()

data class SuccessRnd(
    val title: String,
    val link: String,
    val msg: String,
    val place: String,
    val par: Int
) : NeoState()