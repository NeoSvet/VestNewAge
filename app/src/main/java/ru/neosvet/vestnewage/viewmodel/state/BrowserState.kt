package ru.neosvet.vestnewage.viewmodel.state

import ru.neosvet.vestnewage.helper.BrowserHelper

sealed class BrowserState : NeoState {
    enum class Type {
        NEW_BOOK, OLD_BOOK, DOCTRINE
    }

    data class Primary(
        val url: String,
        val link: String,
        val position: Float,
        val type: Type
    ) : PrimaryState

    data class Status(
        val helper: BrowserHelper,
        val search: String?,
        val index: Int,
        val head: Byte,
        val bottom: Boolean
    ) : StatusState
}
