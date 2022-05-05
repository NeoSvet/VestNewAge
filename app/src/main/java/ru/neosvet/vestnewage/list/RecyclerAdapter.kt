package ru.neosvet.vestnewage.list

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R

class RecyclerAdapter(private val clicker: ItemClicker) :
    RecyclerView.Adapter<RecyclerAdapter.ViewHolder>() {
    interface ItemClicker {
        fun onItemClick(index: Int, item: ListItem)
    }

    companion object {
        private const val TYPE_SIMPLE = 0
        private const val TYPE_DETAIL = 1
    }

    private val data = mutableListOf<ListItem>()

    @SuppressLint("NotifyDataSetChanged")
    fun setItems(items: List<ListItem>) {
        data.clear()
        data.addAll(items)
        notifyDataSetChanged()
    }

    fun addItem(item: ListItem) {
        val i = data.size
        data.add(item)
        notifyItemInserted(i)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clear() {
        data.clear()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = data.size

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

    inner class ViewHolder(private val root: View) : RecyclerView.ViewHolder(root) {
        private val tvTitle: TextView = root.findViewById(R.id.text_item)
        private val tvDes: TextView? = root.findViewById(R.id.des_item)
        private var index: Int = 0

        init {
            val item: View = root.findViewById(R.id.item_bg)
            item.setBackgroundResource(R.drawable.item_bg)
        }

        fun setItem(index: Int) {
            this.index = index
            tvTitle.text = data[index].title
            tvDes?.text = data[index].des
            root.setOnClickListener {
                clicker.onItemClick(index, data[index])
            }
        }
    }
}