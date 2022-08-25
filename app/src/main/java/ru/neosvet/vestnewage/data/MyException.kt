package ru.neosvet.vestnewage.data

import android.view.View
import com.google.android.material.snackbar.Snackbar
import ru.neosvet.vestnewage.R

class MyException(message: String) : Exception(message)

class BaseIsBusyException : Exception() {
    private var snackbar: Snackbar? = null
    fun show(view: View) {
        val context = view.context
        snackbar = Snackbar.make(
            view,
            context.getString(R.string.busy_base_error),
            Snackbar.LENGTH_SHORT
        ).setBackgroundTint(context.getColor(R.color.colorPrimary))
            .setTextColor(context.getColor(android.R.color.white))
            .setActionTextColor(context.getColor(R.color.colorAccentLight))
            .setAction(android.R.string.ok) {
                snackbar?.dismiss()
            }
        snackbar?.show()
    }
}

