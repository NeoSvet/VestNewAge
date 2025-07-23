package ru.neosvet.vestnewage.view.basic

import android.content.Context
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.android.material.snackbar.Snackbar
import ru.neosvet.vestnewage.R

class NeoSnackbar {
    private var snackbar: Snackbar? = null
    val isShown: Boolean
        get() = snackbar?.isShown == true

    fun show(view: View, msg: String, event: (() -> Unit)? = null) {
        val context = view.context
        snackbar = Snackbar.make(
            view, msg,
            Snackbar.LENGTH_INDEFINITE
        ).setBackgroundTint(context.getColor(R.color.colorPrimary))
            .setTextColor(context.getColor(R.color.main_color))
            .setActionTextColor(context.getColor(R.color.colorAccentLight))
            .setAction(android.R.string.ok) {
                if (msg == context.getString(R.string.network_issues))
                    showAboutNoResponse(context)
                else event?.invoke()
                hide()
            }
        snackbar?.show()
    }

    private fun showAboutNoResponse(context: Context) {
        val builder = AlertDialog.Builder(context, R.style.NeoDialog)
            .setTitle(context.resources.getStringArray(R.array.help_title)[10])
            .setMessage(context.getString(R.string.about_no_response))
            .setNegativeButton(
                context.getString(android.R.string.ok)
            ) { dialog, _ -> dialog.dismiss() }
        builder.create().show()
    }

    fun hide() {
        snackbar?.dismiss()
        snackbar = null
    }
}