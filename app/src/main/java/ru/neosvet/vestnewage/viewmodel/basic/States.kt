package ru.neosvet.vestnewage.viewmodel.basic

import ru.neosvet.vestnewage.data.CalendarItem
import ru.neosvet.vestnewage.data.ListItem

sealed class NeoState {
    object None : NeoState()
    object Loading : NeoState()
    object NoConnected : NeoState()
    data class Progress(val percent: Int) : NeoState()
    data class Message(val message: String) : NeoState()
    object Ready : NeoState()
    object Success : NeoState()
    data class LongValue(val value: Long) : NeoState()

    class Error(
        val message: String,
        val information: String
    ) : NeoState() {
        val isNeedReport: Boolean
            get() = information.isNotEmpty()
    }

    data class ListValue(
        val list: List<ListItem>
    ) : NeoState()

    data class ListState(
        val event: ListEvent,
        val index: Int = -1
    ) : NeoState()

    data class Calendar(
        val date: String,
        val list: List<CalendarItem>
    ) : NeoState()

    data class Book(
        val date: String,
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