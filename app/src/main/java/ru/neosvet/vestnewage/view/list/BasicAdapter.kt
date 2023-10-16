package ru.neosvet.vestnewage.view.list

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.view.list.holder.BasicHolder

class BasicAdapter(
    private val clicker: (Int, BasicItem) -> Unit,
    private val longClicker: ((Int, BasicItem) -> Boolean)? = null
) : RecyclerView.Adapter<BasicHolder>() {
    companion object {
        private const val TYPE_TITLE = 0
        private const val TYPE_SIMPLE = 1
        private const val TYPE_DETAIL = 2
    }

    private val data = mutableListOf<BasicItem>()

    @SuppressLint("NotifyDataSetChanged")
    fun setItems(items: List<BasicItem>) {
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
        if (data[position].link == "#")
            TYPE_TITLE
        else if (data[position].des.isEmpty())
            TYPE_SIMPLE
        else
            TYPE_DETAIL

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BasicHolder {
        val layout = when (viewType) {
            TYPE_SIMPLE ->
                LayoutInflater.from(parent.context).inflate(R.layout.item_list, null)
            TYPE_TITLE ->
                LayoutInflater.from(parent.context).inflate(R.layout.item_title, null)
            else ->
                LayoutInflater.from(parent.context).inflate(R.layout.item_detail, null)
        }
        return BasicHolder(layout, clicker, longClicker)
    }

    override fun onBindViewHolder(holder: BasicHolder, position: Int) {
        holder.setItem(data[position])
    }
}