package ru.neosvet.vestnewage.list

data class MarkerItem(
    val id: Int,
    var title: String,
    var data: String,
    var des: String = "",
    var place: String? = null
)