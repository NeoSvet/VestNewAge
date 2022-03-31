package ru.neosvet.vestnewage.model

import ru.neosvet.vestnewage.list.ListItem

sealed class SearchState {
    data class Result(val results: ArrayList<ListItem>, val pages: Int): SearchState()
    data class Status(val text: String): SearchState()
    data class Error(val throwable: Throwable): SearchState()
}