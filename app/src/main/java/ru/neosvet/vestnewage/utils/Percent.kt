package ru.neosvet.vestnewage.utils

fun Int.percent(base: Int) =
    (this.toFloat() / base.toFloat() * 100f).toInt()
