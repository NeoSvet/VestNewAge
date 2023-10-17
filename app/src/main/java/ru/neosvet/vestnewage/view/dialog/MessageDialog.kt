package ru.neosvet.vestnewage.view.dialog

import android.app.Activity
import android.content.DialogInterface
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import ru.neosvet.vestnewage.R

class MessageDialog(act: Activity) {
    private val dialog: AlertDialog
    private val root: View

    init {
        val inflater = act.layoutInflater
        root = inflater.inflate(R.layout.dialog_layout, null)
        val builder = AlertDialog.Builder(act)
        builder.setView(root)
        dialog = builder.create()
    }

    fun show(listener: DialogInterface.OnDismissListener?) {
        if (listener != null) dialog.setOnDismissListener(listener)
        dialog.show()
    }

    fun dismiss() {
        dialog.dismiss()
    }

    fun setTitle(title: String) {
        val tv = root.findViewById<TextView>(R.id.title)
        tv.text = title
    }

    fun setMessage(msg: String) {
        val tv = root.findViewById<TextView>(R.id.message)
        tv.text = msg
    }

    fun setLeftButton(title: String, click: View.OnClickListener) {
        val button = root.findViewById<Button>(R.id.leftButton)
        button.text = title
        button.setOnClickListener(click)
        button.visibility = View.VISIBLE
    }

    fun setRightButton(title: String, click: View.OnClickListener) {
        val button = root.findViewById<Button>(R.id.rightButton)
        button.text = title
        button.setOnClickListener(click)
        button.visibility = View.VISIBLE
    }
}