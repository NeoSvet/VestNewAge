package ru.neosvet.vestnewage.view.list

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.MarkerItem

class MarkerAdapter(
    private val clicker: ((Int) -> Unit),
    private val selector: ((Int) -> Unit)
) : RecyclerView.Adapter<MarkerAdapter.ViewHolder>() {
    companion object {
        private const val TYPE_SIMPLE = 0
        private const val TYPE_DETAIL = 1
    }

    var selectedIndex = -1
        private set
    val selectedItem: MarkerItem?
        get() = if (selectedIndex == -1) null else data[selectedIndex]
    private val data = mutableListOf<MarkerItem>()

    override fun getItemCount(): Int = data.size

    fun selected(index: Int) {
        val prevSel = selectedIndex
        selectedIndex = index
        if (index > -1)
            notifyItemChanged(index)
        if (prevSel > -1)
            notifyItemChanged(prevSel)
    }

    fun update(index: Int, item: MarkerItem) {
        data[index] = item
        notifyItemChanged(index)
    }

    fun remove(index: Int, minIndex: Int) {
        data.removeAt(index)
        val i = when (data.size) {
            minIndex -> -1
            selectedIndex -> selectedIndex - 1
            else -> selectedIndex
        }
        if (i != selectedIndex) selected(i)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setItems(items: List<MarkerItem>) {
        selectedIndex = -1
        data.clear()
        data.addAll(items)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (data[position].des.isEmpty())
            TYPE_SIMPLE else TYPE_DETAIL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return if (viewType == TYPE_SIMPLE)
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_list, null))
        else
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_detail, null))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.setItem(position)
    }

    inner class ViewHolder(root: View) : RecyclerView.ViewHolder(root) {
        private val tvTitle: TextView = root.findViewById(R.id.text_item)
        private val tvDes: TextView? = root.findViewById(R.id.des_item)
        private val itemBg: View = root.findViewById(R.id.item_bg)
        private var index: Int = 0

        init {
            itemBg.setOnClickListener {
                if (selectedIndex > -1) selector.invoke(index)
                else clicker(index)

            }
            itemBg.setOnLongClickListener {
                if (selectedIndex == -1) {
                    selector.invoke(index)
                    true
                } else false
            }
        }

        fun setItem(index: Int) {
            this.index = index
            tvTitle.text = data[index].title
            tvDes?.text = data[index].des
            if (index == selectedIndex)
                itemBg.setBackgroundResource(R.drawable.select_item_bg)
            else
                itemBg.setBackgroundResource(R.drawable.item_bg)
        }
    }
}