package ru.neosvet.vestnewage.model.state

import ru.neosvet.vestnewage.list.ListItem

sealed class SiteState {
    object Loading : SiteState()
    data class Result(
        val list: List<ListItem>
    ) : SiteState()

    data class CheckTime(val sec: Long) : SiteState()
    data class Error(val throwable: Throwable) : SiteState()
}
