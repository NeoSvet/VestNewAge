package ru.neosvet.vestnewage.list

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.list.item.ListItem

class RecyclerAdapter(
    private val clicker: (Int, ListItem) -> Unit,
    private val longClicker: ((Int, ListItem) -> Boolean)? = null
) : RecyclerView.Adapter<RecyclerHolder>() {
    companion object {
        private const val TYPE_SIMPLE = 0
        private const val TYPE_DETAIL = 1
    }

    private val data = mutableListOf<ListItem>()

    @SuppressLint("NotifyDataSetChanged")
    fun setItems(items: List<ListItem>) {
        data.clear()
        data.addAll(items)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clear() {
        data.clear()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = data.size

    override fun getItemViewType(position: Int): Int =
        if (data[position].des.isEmpty())
            TYPE_SIMPLE else TYPE_DETAIL

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerHolder {
        val layout = if (viewType == TYPE_SIMPLE)
            LayoutInflater.from(parent.context).inflate(R.layout.item_list, null)
        else
            LayoutInflater.from(parent.context).inflate(R.layout.item_detail, null)
        return RecyclerHolder(layout, clicker, longClicker)
    }

    override fun onBindViewHolder(holder: RecyclerHolder, position: Int) {
        holder.setItem(data[position])
    }
}