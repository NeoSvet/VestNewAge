package ru.neosvet.vestnewage.data

data class HelpItem(
    val title: String,
    val content: String = "",
    var opened: Boolean = false,
    val icon: Int = 0
)