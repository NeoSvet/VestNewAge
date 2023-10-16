package ru.neosvet.vestnewage.view.list.holder

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R

class EmptyHolder(root: View) : RecyclerView.ViewHolder(root)

class SimpleHolder(
    root: View,
    clicker: ((Int) -> Unit)?
) : RecyclerView.ViewHolder(root) {
    private val tvTitle: TextView = root.findViewById(R.id.text_item)
    private val ivImage: ImageView = root.findViewById(R.id.image_item)

    init {
        val item: View = root.findViewById(R.id.item_bg)
        item.setBackgroundResource(R.drawable.item_bg)
        clicker?.let { event ->
            root.setOnClickListener {
                event.invoke(layoutPosition)
            }
        }
    }

    fun setItem(text: String, icon: Int = -1) {
        tvTitle.text = text
        if (icon == -1) ivImage.isVisible = false
        else {
            ivImage.isVisible = true
            ivImage.setImageDrawable(ContextCompat.getDrawable(ivImage.context, icon))
        }
    }
}