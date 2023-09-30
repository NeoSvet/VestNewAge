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
    private val menu: MutableList<Section>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    interface Events {
        fun onItemClick(type: HomeItem.Type, action: HomeHolder.Action)
        fun onMenuClick(index: Int, section: Section)
        fun onItemMove(holder: RecyclerView.ViewHolder)
    }

    var loadingIndex = -1
        private set
    var isTall = false

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
                LayoutInflater.from(parent.context).inflate(
                    if (isTall) R.layout.item_home_menu_tall else R.layout.item_home_menu, null
                ),
                events::onMenuClick
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
        when (holder) {
            is HomeHolder -> {
                holder.setItem(items[position])
                if (position == loadingIndex)
                    holder.startLoading()
            }

            is HomeEditHolder ->
                holder.setItem(items[position])

            is HomeMenuHolder -> {
                holder.setCell(0, menu[0])
                holder.setCell(1, menu[1])
                holder.setCell(2, menu[2])
                holder.setCell(3, menu[3])
            }
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

    fun changeMenu(index: Int, section: Section) {
        menu[index] = section
        for (i in items.indices)
            if (items[i].type == HomeItem.Type.MENU) {
                notifyItemChanged(i)
                return
            }
    }
}