package ru.neosvet.vestnewage.view.list

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.HomeItem
import ru.neosvet.vestnewage.data.Section

class HomeAdapter(
    private val onItem: (HomeItem.Type, HomeHolder.Action) -> Unit,
    private val onMenu: (Section) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val data = mutableListOf<HomeItem>()
    var loadingIndex = -1
        private set

    @SuppressLint("NotifyDataSetChanged")
    fun setItems(items: List<HomeItem>) {
        loadingIndex = -1
        data.clear()
        data.addAll(items)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clear() {
        loadingIndex = -1
        data.clear()
        notifyDataSetChanged()
    }

    fun startLoading(index: Int) {
        finishLoading()
        loadingIndex = index
        notifyItemChanged(index)
    }

    fun finishLoading() {
        if (loadingIndex == -1) return
        val i = loadingIndex
        loadingIndex = -1
        notifyItemChanged(i)
    }

    fun update(index: Int, item: HomeItem) {
        if (loadingIndex == index) loadingIndex = -1
        data[index] = item
        notifyItemChanged(index)
    }

    override fun getItemCount(): Int = data.size

    override fun getItemViewType(position: Int): Int = data[position].type.value

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            HomeItem.Type.MENU.value -> HomeMenuHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_home_menu, null),
                onMenu
            )

// TODO  HomeItem.Type.FEED.value ->

            else -> HomeHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_home, null),
                onItem
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HomeHolder) {
            holder.setItem(data[position])
            if (position == loadingIndex)
                holder.startLoading()
        }
    }
}