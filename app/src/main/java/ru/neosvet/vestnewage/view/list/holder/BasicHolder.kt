package ru.neosvet.vestnewage.view.list.holder

import android.view.View
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem

class BasicHolder(
    private val root: View,
    private val clicker: (Int, BasicItem) -> Unit,
    private val longClicker: ((Int, BasicItem) -> Boolean)?
) : RecyclerView.ViewHolder(root) {
    private val tvTitle: TextView = root.findViewById(R.id.text_item)
    private val tvTime: TextView? = root.findViewById(R.id.time_item)
    private val tvDes: TextView? = root.findViewById(R.id.des_item)

    init {
        val item: View = root.findViewById(R.id.item_bg)
        item.setBackgroundResource(R.drawable.item_bg)
    }

    val String.fromHTML: CharSequence
        get() = HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_LEGACY).trimEnd()

    fun setItem(item: BasicItem) {
        tvTitle.text = if (item.title.contains("<"))
            item.title.fromHTML else item.title

        val des = tvTime?.let { //for addition
            val i = item.des.indexOf("$")
            it.text = item.des.substring(0, i)
            item.des.substring(i + 1)
        } ?: item.des

        tvDes?.text = if (des.contains("<"))
            des.fromHTML else des

        root.setOnClickListener {
            clicker.invoke(layoutPosition, item)
        }
        longClicker?.let { event ->
            root.setOnLongClickListener {
                event.invoke(layoutPosition, item)
            }
        }
    }
}