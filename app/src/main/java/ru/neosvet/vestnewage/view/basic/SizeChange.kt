package ru.neosvet.vestnewage.view.basic

import android.graphics.Rect
import android.view.View

//from https://stackoverflow.com/a/67018854/2956830
inline fun View?.onSizeChange(crossinline runnable: () -> Unit) = this?.apply {
    addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
        val rect = Rect(left, top, right, bottom)
        val oldRect = Rect(oldLeft, oldTop, oldRight, oldBottom)
        if (rect.width() != oldRect.width() || rect.height() != oldRect.height()) {
            runnable()
        }
    }
}