package ru.neosvet.vestnewage.viewmodel.state

import android.graphics.Point
import ru.neosvet.vestnewage.data.BasicItem

sealed class BookState: NeoState {
    data class Primary(
        val time: Long,
        val label: String,
        val selected: Point,
        val years: List<String>,
        val months: List<String>,
        val list: List<BasicItem>
    ) : PrimaryState
    data class Status(
        val selectedTab: Int,
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
