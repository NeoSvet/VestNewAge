package ru.neosvet.vestnewage.view.list

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.view.list.holder.BaseHolder
import ru.neosvet.vestnewage.view.list.holder.BasicHolder
import ru.neosvet.vestnewage.view.list.holder.ListHolder

class BasicAdapter(
    private val clicker: (Int, BasicItem) -> Unit,
    private val longClicker: ((Int, BasicItem) -> Boolean)? = null
) : RecyclerView.Adapter<BasicHolder>() {
    companion object {
        const val LABEL_SEPARATOR = '$'
        private const val TYPE_TITLE = 0
        private const val TYPE_SIMPLE = 1
        private const val TYPE_DETAIL = 2
        private const val TYPE_LIST = 3
        private const val TYPE_LABEL = 4
    }

    private val data = mutableListOf<BasicItem>()
    private var indexLink = -1

    fun openLinksFor(index: Int) {
        val i = indexLink
        indexLink = -1
        if (i > -1)
            notifyItemChanged(i)
        if (i == index || index == -1) return
        indexLink = index
        notifyItemChanged(indexLink)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setItems(items: List<BasicItem>) {
        data.clear()
        data.addAll(items)
        notifyDataSetChanged()
    }

    fun update(index: Int, item: BasicItem) {
        data[index] = item
        notifyItemChanged(index)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clear() {
        indexLink = -1
        data.clear()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = data.size

    override fun getItemViewType(position: Int): Int = when {
        indexLink == position -> TYPE_LIST
        data[position].link == "#" -> TYPE_TITLE
        data[position].des.isEmpty() -> TYPE_SIMPLE
        data[position].des[0] == LABEL_SEPARATOR -> TYPE_LABEL
        else -> TYPE_DETAIL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BasicHolder {
        val layout = when (viewType) {
            TYPE_SIMPLE ->
                LayoutInflater.from(parent.context).inflate(R.layout.item_text, null)

            TYPE_TITLE ->
                LayoutInflater.from(parent.context).inflate(R.layout.item_title, null)

            TYPE_DETAIL ->
                LayoutInflater.from(parent.context).inflate(R.layout.item_detail, null)

            TYPE_LABEL ->
                LayoutInflater.from(parent.context).inflate(R.layout.item_label, null)

            else ->
                return ListHolder(
                    LayoutInflater.from(parent.context).inflate(R.layout.item_list, null),
                    clicker, longClicker
                )
        }
        return BaseHolder(layout, clicker, longClicker)
    }

    override fun onBindViewHolder(holder: BasicHolder, position: Int) {
        holder.setItem(data[position])
    }
}