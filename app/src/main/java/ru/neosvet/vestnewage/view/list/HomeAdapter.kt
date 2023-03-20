package ru.neosvet.vestnewage.view.list

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.HomeItem

class HomeAdapter(
    private val onItem: (HomeItem.Type, HomeHolder.Action) -> Unit,
    private val onMenu: (HomeMenuHolder.Action) -> Unit
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
        loadingIndex = index
        data[index].isLoading = true
        notifyItemChanged(index)
    }

    fun finishLoading(index: Int) {
        if (loadingIndex == index) loadingIndex = -1
        data[index].isLoading = false
        notifyItemChanged(index)
    }

    fun update(item: HomeItem) {
        for (i in data.indices) {
            if (data[i].line1 == item.line1) {
                if (loadingIndex == i) loadingIndex = -1
                data.removeAt(i)
                data.add(i, item)
                notifyItemChanged(i)
                return
            }
        }
    }

    override fun getItemCount(): Int = data.size

    override fun getItemViewType(position: Int): Int =
        if (data[position].type == HomeItem.Type.MENU) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == 0) HomeHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_home, null),
            onItem
        )
        else HomeMenuHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_home_menu, null),
            onMenu
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HomeHolder)
            holder.setItem(data[position])
    }
}