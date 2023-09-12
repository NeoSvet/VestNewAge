package ru.neosvet.vestnewage.view.list

import android.text.Html
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem

class RecyclerHolder(
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

    fun setItem(item: BasicItem) {
        if (item.title.contains("<"))
            tvTitle.text = Html.fromHtml(item.title).trimEnd()
        else
            tvTitle.text = item.title
        val des = tvTime?.let { //for addition
            val i = item.des.indexOf("@")
            it.text = item.des.substring(0, i)
            item.des.substring(i + 1)
        } ?: item.des
        tvDes?.let {
            if (des.contains("<"))
                it.text = Html.fromHtml(des).trimEnd()
            else
                it.text = des
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