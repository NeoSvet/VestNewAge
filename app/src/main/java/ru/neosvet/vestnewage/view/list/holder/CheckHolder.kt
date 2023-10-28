package ru.neosvet.vestnewage.view.list.holder

import android.view.View
import android.widget.CheckedTextView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem

class CheckHolder(
    root: View,
    private val changeCheck: (Int, Boolean) -> Unit
) : BasicHolder(root) {
    private val checkBox: CheckedTextView = root.findViewById(R.id.check_box)

    init {
        checkBox.setOnClickListener {
            val value = checkBox.isChecked.not()
            checkBox.isChecked = value
            changeCheck.invoke(layoutPosition, value)
        }
    }

    var isChecked: Boolean
        get() = checkBox.isChecked
        set(value) {
            checkBox.isChecked = value
        }

    override fun setItem(item: BasicItem) {
        checkBox.text = item.title
        checkBox.isChecked = item.link.isNotEmpty()
    }
}