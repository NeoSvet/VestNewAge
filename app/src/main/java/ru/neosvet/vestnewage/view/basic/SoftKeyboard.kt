package ru.neosvet.vestnewage.view.basic

import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat

class SoftKeyboard(private val view: View) {
    private val imm = ContextCompat.getSystemService(
        view.context,
        InputMethodManager::class.java
    ) as InputMethodManager

    fun show() {
        view.post { imm.showSoftInput(view, 0) }
    }

    fun hide() {
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}