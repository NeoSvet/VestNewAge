package ru.neosvet.vestnewage.view.list

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.HomeItem
import ru.neosvet.vestnewage.data.Section

class HomeAdapter(
    private val events: Events,
    val isEditor: Boolean,
    private val items: MutableList<HomeItem>,
    private val menu: List<Section>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    interface Events {
        fun onItemClick(type: HomeItem.Type, action: HomeHolder.Action)
        fun onMenuClick(section: Section)
        fun onItemMove(holder: RecyclerView.ViewHolder)
    }

    var loadingIndex = -1
        private set

    fun startLoading(index: Int) {
        if (isEditor) return
        finishLoading()
        loadingIndex = index
        notifyItemChanged(index)
    }

    fun finishLoading() {
        if (loadingIndex == -1 || isEditor) return
        val i = loadingIndex
        loadingIndex = -1
        notifyItemChanged(i)
    }

    fun update(index: Int, item: HomeItem) {
        if (loadingIndex == index) loadingIndex = -1
        items[index] = item
        notifyItemChanged(index)
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = items[position].type.value

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            HomeItem.Type.MENU.value -> HomeMenuHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_home_menu, null),
                menu, events::onMenuClick
            )

            HomeItem.Type.DIV.value -> EmptyHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_home_div, null)
            )

            else -> if (isEditor)
                HomeEditHolder(
                    LayoutInflater.from(parent.context).inflate(
                        R.layout.item_home_edit, null
                    ), events::onItemMove
                ) else
                HomeHolder(
                    LayoutInflater.from(parent.context).inflate(
                        R.layout.item_home, null
                    ), events::onItemClick
                )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HomeHolder) {
            holder.setItem(items[position])
            if (position == loadingIndex)
                holder.startLoading()
        } else if (holder is HomeEditHolder) {
            holder.setItem(items[position])
        }
    }

    fun moveUp(index: Int) {
        val item = items[index - 1]
        items.removeAt(index - 1)
        items.add(index, item)
        notifyItemMoved(index, index - 1)
    }

    fun moveDown(index: Int) {
        val item = items[index]
        items.removeAt(index)
        items.add(index + 1, item)
        notifyItemMoved(index, index + 1)
    }
}