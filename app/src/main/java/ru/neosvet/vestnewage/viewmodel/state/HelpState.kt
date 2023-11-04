package ru.neosvet.vestnewage.viewmodel.state

import ru.neosvet.vestnewage.data.HelpItem

sealed class HelpState: NeoState {
    data class Primary(
        val list: List<HelpItem>
    ) : PrimaryState

    enum class Type {
        BEGIN_BOOK, PRIVACY, SITE, TELEGRAM, CHANGELOG, GOOGLE, HUAWEI
    }

    data class Open(
        val type: Type
    ): HelpState()
}