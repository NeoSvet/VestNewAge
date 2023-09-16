package ru.neosvet.vestnewage.viewmodel.state

import ru.neosvet.vestnewage.data.MarkerItem

sealed class MarkersState : NeoState {
    data class Primary(
        val title: String,
        val list: List<MarkerItem>,
        val isCollections: Boolean
    ) : PrimaryState

    enum class Type {
        NONE, RENAME, DELETE
    }

    data class Status(
        val isRotate: Boolean,
        val selectedIndex: Int,
        val dialog: Type
    ) : StatusState

    data object FinishImport : MarkersState()

    data class FinishExport(
        val message: String
    ) : MarkersState()
}