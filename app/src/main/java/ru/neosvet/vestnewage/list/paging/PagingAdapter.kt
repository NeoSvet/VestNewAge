package ru.neosvet.vestnewage.list.paging

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.list.RecyclerHolder
import ru.neosvet.vestnewage.list.item.ListItem

class PagingAdapter(
    private val clicker: (Int, ListItem) -> Unit,
    private val longClicker: ((Int, ListItem) -> Boolean)?,
    private val finishedList: () -> Unit
) : PagingDataAdapter<ListItem, RecyclerHolder>(ItemsComparator) {
    companion object {
        private const val TYPE_SIMPLE = 0
        private const val TYPE_DETAIL = 1
    }

    private var isFirst = true

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return if (item?.des?.isNotEmpty() == true)
            TYPE_DETAIL else TYPE_SIMPLE
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerHolder {
        val layout = if (viewType == TYPE_SIMPLE)
            LayoutInflater.from(parent.context).inflate(R.layout.item_list, null)
        else
            LayoutInflater.from(parent.context).inflate(R.layout.item_detail, null)
        return RecyclerHolder(layout, clicker, longClicker)
    }

    override fun onBindViewHolder(holder: RecyclerHolder, position: Int) {
        val item = getItem(position)
        when {
            isFirst -> isFirst = false
            position == 0 -> finishedList.invoke()
            position == itemCount - 1 -> finishedList.invoke()
        }
        item?.let { holder.setItem(it) }
    }

    fun update(item: ListItem) {
        if (itemCount == 0) return
        for (i in 0..itemCount) {
            getItem(i)?.let {
                if (it.link == item.link) {
                    it.title = item.title
                    it.des = item.des
                    notifyItemChanged(i)
                    return
                }
            }
        }
    }

    object ItemsComparator : DiffUtil.ItemCallback<ListItem>() {
        override fun areItemsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
            return oldItem.title == newItem.title
        }

        override fun areContentsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
            return oldItem.des == newItem.des && oldItem.link == newItem.link
        }
    }
}