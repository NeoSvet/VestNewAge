package ru.neosvet.vestnewage.view.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R

class RequestAdapter(
    private val items: List<String>,
    private val selectItem: (Int) -> Unit,
    private val removeItem: (Int) -> Unit,
    private val clearList: () -> Unit
) : RecyclerView.Adapter<RequestAdapter.Holder>() {
    override fun getItemCount(): Int = items.size + 1

    override fun getItemViewType(position: Int): Int {
        return position
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestAdapter.Holder {
        return if (viewType == 0)
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_menu, null))
        else
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_close, null))
    }

    override fun onBindViewHolder(holder: RequestAdapter.Holder, position: Int) {
        holder.setItem(position)
    }

    inner class Holder(private val root: View) : RecyclerView.ViewHolder(root) {
        fun setItem(index: Int) {
            val tvText: TextView = root.findViewById(R.id.text_item)
            if (index == 0) {
                tvText.text = root.context.getString(R.string.clear_list)
                root.setBackgroundResource(R.drawable.press)
                root.setOnClickListener {
                    clearList.invoke()
                }
                val ivImage: ImageView = root.findViewById(R.id.image_item)
                ivImage.setImageResource(R.drawable.ic_clear)
                return
            }
            val p = index - 1
            tvText.text = items[p]
            tvText.setOnClickListener {
                selectItem.invoke(p)
            }
            root.findViewById<View>(R.id.close_item).setOnClickListener {
                removeItem.invoke(p)
            }
        }
    }
}