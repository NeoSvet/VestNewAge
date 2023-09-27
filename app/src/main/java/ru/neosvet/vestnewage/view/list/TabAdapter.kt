package ru.neosvet.vestnewage.view.list

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.view.list.TabAdapter.Holder

class TabAdapter(
    private val onItem: (Int) -> Unit
) : RecyclerView.Adapter<Holder>() {
    private val items = mutableListOf<String>()
    var selected = 0
        private set

    @SuppressLint("NotifyDataSetChanged")
    fun setItems(list: List<String>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun select(index: Int) {
        val i = selected
        selected = index
        notifyItemChanged(i)
        notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val layout = LayoutInflater.from(parent.context).inflate(R.layout.item_tab, null)
        return Holder(layout)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.setItem(items[position])
    }

    inner class Holder(root: View) : RecyclerView.ViewHolder(root) {
        private val tv: TextView = root.findViewById(android.R.id.text1)

        fun setItem(caption: String) {
            tv.text = caption
            val index = absoluteAdapterPosition
            if (index == selected)
                tv.background = ContextCompat.getDrawable(tv.context, R.drawable.selected_tab)
            else tv.setBackgroundColor(
                ContextCompat.getColor(tv.context, android.R.color.transparent)
            )
            tv.setOnClickListener{
                onItem.invoke(index)
            }
        }
    }

}