package ru.neosvet.vestnewage.viewmodel.basic

import ru.neosvet.vestnewage.data.CalendarItem
import ru.neosvet.vestnewage.data.ListItem

sealed class NeoState {
    object Loading : NeoState()
    object NoConnected : NeoState()
    data class Error(val throwable: Throwable) : NeoState()
}

object Ready : NeoState()

object Success : NeoState()

data class LongState(val value: Long) : NeoState()

data class ProgressState(val percent: Int) : NeoState()

data class MessageState(val message: String) : NeoState()

data class SuccessList(
    val list: List<ListItem>
) : NeoState()

enum class ListEvent {
    REMOTE, CHANGE, MOVE, RELOAD
}

data class UpdateList(
    val event: ListEvent,
    val index: Int = -1
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

data class AdsState(
    val hasNew: Boolean,
    val warnIndex: Int,
    val timediff: Int
) : NeoState()