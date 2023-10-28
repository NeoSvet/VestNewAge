package ru.neosvet.vestnewage.viewmodel.state

import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.data.CabinetScreen

sealed class CabinetState: NeoState {
    data class Primary(
        val screen: CabinetScreen,
        val list: List<BasicItem>,
    ) : PrimaryState
}
