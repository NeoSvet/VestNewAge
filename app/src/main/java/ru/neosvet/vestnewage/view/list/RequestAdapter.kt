package ru.neosvet.vestnewage.view.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R

class RequestAdapter(
    private val items: List<String>,
    private val selectItem: (Int) -> Unit,
    private val removeItem: (Int) -> Unit,
) : RecyclerView.Adapter<RequestAdapter.Holder>() {
    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestAdapter.Holder {
        return Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_close, null))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.setItem(items[position])
    }

    inner class Holder(val root: View) : RecyclerView.ViewHolder(root) {
        private val tvText: TextView = root.findViewById(R.id.text_item)

        init {
            tvText.setOnClickListener {
                selectItem.invoke(absoluteAdapterPosition)
            }
            root.findViewById<View>(R.id.close_item).setOnClickListener {
                removeItem.invoke(absoluteAdapterPosition)
            }
        }

        fun setItem(item: String) {
            tvText.text = item
        }
    }
}