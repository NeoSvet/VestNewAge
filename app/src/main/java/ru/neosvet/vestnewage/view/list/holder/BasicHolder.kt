package ru.neosvet.vestnewage.view.list.holder

import android.view.View
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.view.list.BasicAdapter

abstract class BasicHolder(root: View) : RecyclerView.ViewHolder(root) {

    val String.fromHTML: CharSequence
        get() = HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_LEGACY).trimEnd()

    abstract fun setItem(item: BasicItem)
}

class BaseHolder(
    private val root: View,
    private val clicker: (Int, BasicItem) -> Unit,
    private val longClicker: ((Int, BasicItem) -> Boolean)?
) : BasicHolder(root) {
    private val tvTitle: TextView = root.findViewById(R.id.text_item)

    init {
        val item: View = root.findViewById(R.id.item_bg)
        item.setBackgroundResource(R.drawable.item_bg)
    }

    override fun setItem(item: BasicItem) {
        tvTitle.text = if (item.title.contains("<"))
            item.title.fromHTML else item.title
        val des = root.findViewById<TextView>(R.id.label_item)?.let {
            val i = item.des.indexOf(BasicAdapter.LABEL_SEPARATOR, 2)
            it.text = item.des.substring(1, i)
            item.des.substring(i + 1)
        } ?: item.des

        root.findViewById<TextView>(R.id.des_item)?.text =
            if (des.contains("<")) des.fromHTML else des

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