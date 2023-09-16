package ru.neosvet.vestnewage.data

enum class Section(val value: Int) {
    MENU(0), HOME(1), CALENDAR(2), SUMMARY(3), NEW(4), BOOK(5),
    SITE(6), SEARCH(7), MARKERS(8), JOURNAL(9), CABINET(10),
    SETTINGS(11), HELP(12)
}

enum class BookTab(val value: Int) {
    POEMS(0), EPISTLES(1), DOCTRINE(2)
}

enum class BookRnd {
    POEM, EPISTLE, VERSE
}

enum class SiteTab(val value: Int) {
    NEWS(0), SITE(1), DEV(2)
}

enum class SummaryTab(val value: Int) {
    RSS(0), ADDITION(1)
}

enum class CabinetScreen {
    LOGIN, CABINET, WORDS
}

enum class MarkerScreen {
    NONE, PARAGRAPH, POSITION, COLLECTION
}