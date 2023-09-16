package ru.neosvet.vestnewage.viewmodel.state

import ru.neosvet.vestnewage.data.BasicItem

sealed class NewState : NeoState {
    data class Status(
        val itemAds: BasicItem?
    ) : StatusState
}
