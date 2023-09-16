package ru.neosvet.vestnewage.viewmodel.state

import ru.neosvet.vestnewage.data.BasicItem

sealed class SiteState : NeoState {
    data class Status(
        val selectedTab: Int,
        val itemAds: BasicItem?
    ) : StatusState
}
