package ru.neosvet.vestnewage.viewmodel.state

import ru.neosvet.vestnewage.helper.BrowserHelper

sealed class BrowserState : NeoState {
    data class Primary(
        val helper: BrowserHelper
    ) : PrimaryState
    data class Status(
        val search: String?
    ) : StatusState
    data class Page(
        val url: String,
        val isOtkr: Boolean = false
    ) : BrowserState()
}
