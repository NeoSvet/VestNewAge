package ru.neosvet.vestnewage.view.list.paging

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.view.list.holder.BaseHolder
import ru.neosvet.vestnewage.view.list.holder.BasicHolder
import ru.neosvet.vestnewage.view.list.holder.ListHolder

class PagingAdapter(
    private val parent: Parent
) : PagingDataAdapter<BasicItem, BasicHolder>(ItemsComparator), NeoPaging.Pager {
    interface Parent {
        fun onItemClick(index: Int, item: BasicItem)
        fun onItemLongClick(index: Int, item: BasicItem): Boolean
        fun onChangePage(page: Int)
        fun onFinishList(endList: Boolean)
    }

    companion object {
        private const val TYPE_SIMPLE = 0
        private const val TYPE_DETAIL = 1
        private const val TYPE_LIST = 2
    }

    var withTime = false //for addition 16.11.2022 14:02
    private var startPage = 0
    private var prevPage = 0
    private lateinit var recyclerView: RecyclerView
    private lateinit var manager: GridLayoutManager
    private var indexLink = -1

    val firstPosition: Int
        get() = startPage * NeoPaging.ON_PAGE + manager.findFirstVisibleItemPosition()

    override fun setPage(page: Int) {
        indexLink = -1
        startPage = page
    }

    fun openLinksFor(index: Int) {
        val i = indexLink
        indexLink = -1
        if (i > -1)
            notifyItemChanged(i)
        if (i == index || index == -1) return
        indexLink = index
        notifyItemChanged(indexLink)
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
                if (itemCount > 0)
                    recyclerView.smoothScrollToPosition(itemCount - 1)
                prevPage = -1
            } else if (!recyclerView.canScrollVertically(-1))
                parent.onFinishList(false)
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (indexLink == position) return TYPE_LIST
        val item = getItem(position)
        return if (item?.des?.isNotEmpty() == true)
            TYPE_DETAIL else TYPE_SIMPLE
    }

    override fun onCreateViewHolder(host: ViewGroup, viewType: Int): BasicHolder {
        val layout = when {
            viewType == TYPE_LIST -> return ListHolder(
                LayoutInflater.from(host.context).inflate(R.layout.item_list, null),
                parent::onItemClick, parent::onItemLongClick
            )

            withTime -> LayoutInflater.from(host.context).inflate(R.layout.item_label, null)
            viewType == TYPE_SIMPLE -> LayoutInflater.from(host.context)
                .inflate(R.layout.item_text, null)

            else -> LayoutInflater.from(host.context).inflate(R.layout.item_detail, null)
        }
        return BaseHolder(layout, parent::onItemClick, parent::onItemLongClick)
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

    override fun onBindViewHolder(holder: BasicHolder, position: Int) {
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