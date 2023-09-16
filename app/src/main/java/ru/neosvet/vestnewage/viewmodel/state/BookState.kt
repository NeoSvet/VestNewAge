package ru.neosvet.vestnewage.viewmodel.state

import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.data.DateUnit

sealed class BookState: NeoState {
    data class Primary(
        val time: Long,
        val date: DateUnit,
        val prev: Boolean,
        val next: Boolean,
        val list: List<BasicItem>
    ) : PrimaryState
    data class Status(
        val selectedTab: Int,
        val dateDialog: DateUnit?,
        val shownDwnDialog: Boolean
    ) : StatusState
    data class Book(
        val linkToSrc: String,
        val list: List<BasicItem>
    ) : PrimaryState
    data class Rnd(
        val title: String,
        val link: String,
        val msg: String,
        val place: String,
        val par: Int
    ) : SecondState
}
