package ru.neosvet.vestnewage.data

data class HomeItem(
    val type: Type,
    val lines: List<String>,
    val time: Long = 0L
) {
    companion object {
        const val PLACE_TIME = "%s"
    }

    enum class Type(val value: Int) {
        SUMMARY(0), NEWS(1), ADDITION(2), CALENDAR(3),
        INFO(4), JOURNAL(5), MENU(6), DIV(7)
    }

    val hasRefresh: Boolean
        get() = type.value < 4

    val timeString: String?
        get() = if (time == 0L || !lines[1].contains(PLACE_TIME)) null
        else {
            val diff = DateUnit.getDiffDate(System.currentTimeMillis(), time)
            lines[1].format(diff)
        }
}