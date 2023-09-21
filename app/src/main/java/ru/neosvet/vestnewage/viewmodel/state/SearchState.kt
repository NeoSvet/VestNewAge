package ru.neosvet.vestnewage.viewmodel.state

import android.os.Bundle
import ru.neosvet.vestnewage.data.SearchScreen
import ru.neosvet.vestnewage.helper.SearchHelper

sealed class SearchState : NeoState {
    data class Primary(
        val helper: SearchHelper
    ) : PrimaryState

    data object Start: SearchState()

    data class Status(
        val screen: SearchScreen,
        val settings: Bundle?,
        val shownAddition: Boolean,
        val firstPosition: Int,
        val selectPosition: Int
    ) : StatusState

    data class FinishExport(
        val message: String
    ) : SearchState()

    data class Results(
        val max: Int,
        val finish: Boolean
    ) : SearchState()
}
