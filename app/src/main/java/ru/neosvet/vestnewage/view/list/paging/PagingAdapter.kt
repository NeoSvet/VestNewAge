package ru.neosvet.vestnewage.view.list.paging

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.view.list.RecyclerHolder

class PagingAdapter(
    private val parent: Parent
) : PagingDataAdapter<ListItem, RecyclerHolder>(ItemsComparator), NeoPaging.Pager {
    interface Parent {
        fun onItemClick(index: Int, item: ListItem)
        fun onItemLongClick(index: Int, item: ListItem): Boolean
        fun onChangePage(page: Int)
        fun onFinishList()
    }

    companion object {
        private const val TYPE_SIMPLE = 0
        private const val TYPE_DETAIL = 1
    }

    private var startPage = 0
    private var isFirst = true
    private var prevPage = 0
    private lateinit var manager: GridLayoutManager

    override fun setPage(page: Int) {
        startPage = page
    }

    private val scroller = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            val p = manager.findFirstVisibleItemPosition()
            val page = p / NeoPaging.ON_PAGE + startPage
            if (prevPage != page) {
                prevPage = page
                parent.onChangePage(page)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return if (item?.des?.isNotEmpty() == true)
            TYPE_DETAIL else TYPE_SIMPLE
    }

    override fun onCreateViewHolder(host: ViewGroup, viewType: Int): RecyclerHolder {
        val layout = if (viewType == TYPE_SIMPLE)
            LayoutInflater.from(host.context).inflate(R.layout.item_list, null)
        else
            LayoutInflater.from(host.context).inflate(R.layout.item_detail, null)
        return RecyclerHolder(layout, parent::onItemClick, parent::onItemLongClick)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        manager = recyclerView.layoutManager as GridLayoutManager
        recyclerView.addOnScrollListener(scroller)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        recyclerView.removeOnScrollListener(scroller)
    }

    override fun onBindViewHolder(holder: RecyclerHolder, position: Int) {
        val item = getItem(position)
        when {
            isFirst -> isFirst = false
            position == 0 -> parent.onFinishList()
            position == itemCount - 1 -> parent.onFinishList()
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