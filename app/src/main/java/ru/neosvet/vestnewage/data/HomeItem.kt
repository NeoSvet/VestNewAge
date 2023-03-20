package ru.neosvet.vestnewage.data

data class HomeItem(
    val type: Type,
    val isRefresh: Boolean,
    val line1: String,
    val line2: String,
    val line3: String,
    var isLoading: Boolean = false
) {
    enum class Type {
        SUMMARY, NEWS, ADDITION, CALENDAR, PROM, JOURNAL, MENU
    }
}