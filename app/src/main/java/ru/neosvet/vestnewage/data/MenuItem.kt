package ru.neosvet.vestnewage.data

import ru.neosvet.vestnewage.R

class MenuItem(var image: Int, var title: String) {
    var isSelect = false
    var isChecked: Boolean
        get() = image == R.drawable.checkbox_simple
        set(value) {
            image = if (value) R.drawable.checkbox_simple
            else R.drawable.uncheckbox_simple
        }
}