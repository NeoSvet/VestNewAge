package ru.neosvet.vestnewage.viewmodel.basic

import ru.neosvet.vestnewage.data.CalendarItem
import ru.neosvet.vestnewage.data.ListItem

sealed class NeoState {
    object Loading : NeoState()
    object NoConnected : NeoState()
    data class Progress(val percent: Int) : NeoState()
    data class Message(val message: String) : NeoState()
    object Ready : NeoState()
    object Success : NeoState()
    data class Error(val throwable: Throwable) : NeoState()
    data class LongState(val value: Long) : NeoState()

    data class ListValue(
        val list: List<ListItem>
    ) : NeoState()

    data class ListState(
        val event: ListEvent,
        val index: Int = -1
    ) : NeoState()

    data class Calendar(
        val date: String,
        val prev: Boolean,
        val next: Boolean,
        val list: List<CalendarItem>
    ) : NeoState()

    data class Book(
        val date: String,
        val prev: Boolean,
        val next: Boolean,
        val list: List<ListItem>
    ) : NeoState()

    data class Page(
        val url: String,
        val isOtkr: Boolean = false
    ) : NeoState()

    data class Rnd(
        val title: String,
        val link: String,
        val msg: String,
        val place: String,
        val par: Int
    ) : NeoState()

    data class Ads(
        val hasNew: Boolean,
        val warnIndex: Int,
        val timediff: Int
    ) : NeoState()
}

enum class ListEvent {
    REMOTE, CHANGE, MOVE, RELOAD
}