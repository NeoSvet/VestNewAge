package ru.neosvet.vestnewage.view.list.paging

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.view.list.RecyclerHolder

class PagingAdapter(
    private val parent: Parent
) : PagingDataAdapter<BasicItem, RecyclerHolder>(ItemsComparator), NeoPaging.Pager {
    interface Parent {
        fun onItemClick(index: Int, item: BasicItem)
        fun onItemLongClick(index: Int, item: BasicItem): Boolean
        fun onChangePage(page: Int)
        fun onFinishList(endList: Boolean)
    }

    companion object {
        private const val TYPE_SIMPLE = 0
        private const val TYPE_DETAIL = 1
    }

    var withTime = false //for addition 16.11.2022 14:02
    private var startPage = 0
    private var prevPage = 0
    private lateinit var recyclerView: RecyclerView
    private lateinit var manager: GridLayoutManager

    val firstPosition: Int
        get() = startPage * NeoPaging.ON_PAGE + manager.findFirstVisibleItemPosition()

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
            if (!recyclerView.canScrollVertically(1)) {
                parent.onFinishList(true)
                recyclerView.smoothScrollToPosition(itemCount - 1)
                prevPage = -1
            } else if (!recyclerView.canScrollVertically(-1))
                parent.onFinishList(false)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return if (item?.des?.isNotEmpty() == true)
            TYPE_DETAIL else TYPE_SIMPLE
    }

    override fun onCreateViewHolder(host: ViewGroup, viewType: Int): RecyclerHolder {
        val layout = if (withTime)
            LayoutInflater.from(host.context).inflate(R.layout.item_time, null)
        else if (viewType == TYPE_SIMPLE)
            LayoutInflater.from(host.context).inflate(R.layout.item_list, null)
        else
            LayoutInflater.from(host.context).inflate(R.layout.item_detail, null)
        return RecyclerHolder(layout, parent::onItemClick, parent::onItemLongClick)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
        manager = recyclerView.layoutManager as GridLayoutManager
        recyclerView.addOnScrollListener(scroller)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        recyclerView.removeOnScrollListener(scroller)
    }

    override fun onBindViewHolder(holder: RecyclerHolder, position: Int) {
        getItem(position)?.let { holder.setItem(it) }
    }

    fun update(item: BasicItem) {
        if (itemCount == 0) return
        for (i in 0 until itemCount) {
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

    object ItemsComparator : DiffUtil.ItemCallback<BasicItem>() {
        override fun areItemsTheSame(oldItem: BasicItem, newItem: BasicItem): Boolean {
            return if (oldItem.head.isEmpty())
                oldItem.title == newItem.title
            else oldItem.head == newItem.head
        }

        override fun areContentsTheSame(oldItem: BasicItem, newItem: BasicItem): Boolean {
            return oldItem.des == newItem.des && oldItem.link == newItem.link
        }
    }
}