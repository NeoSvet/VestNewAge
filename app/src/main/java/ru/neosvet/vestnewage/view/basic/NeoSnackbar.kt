package ru.neosvet.vestnewage.view.basic

import android.view.View
import com.google.android.material.snackbar.Snackbar
import ru.neosvet.vestnewage.R

class NeoSnackbar {
    private var snackbar: Snackbar? = null
    val isShown: Boolean
        get() = snackbar?.isShown == true

    fun show(view: View, msg: String) {
        val context = view.context
        snackbar = Snackbar.make(
            view, msg,
            Snackbar.LENGTH_INDEFINITE
        ).setBackgroundTint(context.getColor(R.color.colorPrimary))
            .setTextColor(context.getColor(android.R.color.white))
            .setActionTextColor(context.getColor(R.color.colorAccentLight))
            .setAction(android.R.string.ok) {
                hide()
            }
        snackbar?.show()
    }

    fun hide() {
        snackbar?.dismiss()
        snackbar = null
    }
}