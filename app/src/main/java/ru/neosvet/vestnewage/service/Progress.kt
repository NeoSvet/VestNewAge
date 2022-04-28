package ru.neosvet.vestnewage.service

data class Progress(
    var text: String = "",
    var max: Int = 0,
    var prog: Int = 0,
    var task: Int = 0,
    var count: Int = 2
) {
    val message: String
        get() = if (count == 0) text
        else "$text ($task/$count)"
}
