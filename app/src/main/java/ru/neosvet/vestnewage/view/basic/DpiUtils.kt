package ru.neosvet.vestnewage.view.basic

import android.content.Context

fun Context.fromDpi(id: Int): Int
    = resources.getDimension(id).toInt()

fun Context.convertDpi(dp: Int): Int
    = (resources.displayMetrics.density * dp).toInt()