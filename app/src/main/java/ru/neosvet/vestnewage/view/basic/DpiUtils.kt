package ru.neosvet.vestnewage.view.basic

import android.content.Context
import com.google.android.material.bottomappbar.BottomAppBar
import ru.neosvet.vestnewage.R

fun Context.fromDpi(id: Int): Int = resources.getDimension(id).toInt()

val Context.defIndent: Int
    get() = resources.getDimension(R.dimen.def_indent).toInt()

fun Context.convertToDpi(px: Int): Int = (resources.displayMetrics.density * px).toInt()

val BottomAppBar.Y: Int
    get() = (translationY / resources.displayMetrics.density).toInt()