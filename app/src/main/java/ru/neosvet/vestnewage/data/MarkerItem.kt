package ru.neosvet.vestnewage.data

data class MarkerItem(
    val id: Int,
    var title: String,
    var data: String,
    var des: String = "",
    var text: String = "",
    var place: String? = null
)