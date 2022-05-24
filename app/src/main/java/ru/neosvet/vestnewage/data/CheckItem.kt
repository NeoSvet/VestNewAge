package ru.neosvet.vestnewage.data

data class CheckItem(
    val title: String,
    var id: Int = 0,
    var isChecked: Boolean = false
)