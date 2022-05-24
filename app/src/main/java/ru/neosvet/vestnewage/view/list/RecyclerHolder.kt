package ru.neosvet.vestnewage.view.list

import android.text.Html
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.ListItem

class RecyclerHolder(
    private val root: View,
    private val clicker: (Int, ListItem) -> Unit,
    private val longClicker: ((Int, ListItem) -> Boolean)?
) : RecyclerView.ViewHolder(root) {
    private val tvTitle: TextView = root.findViewById(R.id.text_item)
    private val tvDes: TextView? = root.findViewById(R.id.des_item)

    init {
        val item: View = root.findViewById(R.id.item_bg)
        item.setBackgroundResource(R.drawable.item_bg)
    }

    fun setItem(item: ListItem) {
        tvTitle.text = item.title
        tvDes?.let {
            if (item.des.contains("<"))
                it.text = Html.fromHtml(item.des)
            else
                it.text = item.des
        }
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