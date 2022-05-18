package ru.neosvet.vestnewage.list.item

data class CheckItem(
    val title: String,
    var id: Int = 0,
    var isChecked: Boolean = false
)