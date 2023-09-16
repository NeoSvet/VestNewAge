package ru.neosvet.vestnewage.viewmodel.state

import ru.neosvet.vestnewage.data.MarkerScreen
import ru.neosvet.vestnewage.helper.MarkerHelper

sealed class MarkerState : NeoState {
    data class Primary(
        val helper: MarkerHelper,
        val title: String,
        val des: String,
        val isPar: Boolean,
        val sel: String,
        val cols: String
    ) : PrimaryState

    data class Status(
        val screen: MarkerScreen,
        val selection: String,
        val positionText: String = "",
        val position: Int = -1
    ) : StatusState

    enum class TextType {
        COL, SEL//, POS
    }

    data class Text(
        val type: TextType,
        val text: String
    ) : MarkerState()
}
