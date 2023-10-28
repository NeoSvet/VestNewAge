package ru.neosvet.vestnewage.view.list.holder

import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem

class InputHolder(
    root: View,
    private val changeText: (Int, String) -> Unit
) : BasicHolder(root) {
    private val til: TextInputLayout = root.findViewById(R.id.til_item)
    private val et: TextInputEditText = root.findViewById(R.id.et_item)
    private val btnClear: View = root.findViewById(R.id.btn_clear)
    private var prevText = ""

    init {
        et.doAfterTextChanged {
            val t = et.text.toString()
            if (t == prevText) return@doAfterTextChanged
            prevText = t
            changeText.invoke(layoutPosition, t)
            btnClear.isVisible = t.isNotEmpty()
        }
        btnClear.setOnClickListener { et.setText("") }
    }

    val text: String
        get() = et.text.toString()

    override fun setItem(item: BasicItem) {
        til.hint = item.title
        et.setText(item.des)
        et.imeOptions = item.link[0].toString().toInt()
        //EditorInfo.IME_ACTION_NEXT
        //EditorInfo.IME_ACTION_DONE
        et.inputType = item.link.substring(1).toInt()
        //EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        //EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
    }
}