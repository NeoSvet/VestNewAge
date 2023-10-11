package ru.neosvet.vestnewage.view.basic

import android.content.Context
import com.google.android.material.bottomappbar.BottomAppBar

fun Context.fromDpi(id: Int): Int
    = resources.getDimension(id).toInt()

fun Context.convertDpi(dp: Int): Int
    = (resources.displayMetrics.density * dp).toInt()

val BottomAppBar.Y: Int
    get() = (translationY / resources.displayMetrics.density).toInt()