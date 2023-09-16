package ru.neosvet.vestnewage.data

data class HomeItem(
    val type: Type,
    val lines: List<String>
) {
    enum class Type(val value: Int) {
        SUMMARY(0), NEWS(1), ADDITION(2), CALENDAR(3),
        PROM(4), JOURNAL(5), MENU(6), FEED(7)
    }

    val hasRefresh: Boolean
        get() = type.value < 4
}