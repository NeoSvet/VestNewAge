package ru.neosvet.vestnewage.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R

class HelpAdapter(
    private val clicker: ItemClicker,
    private val data: List<HelpItem>
) :
    RecyclerView.Adapter<HelpAdapter.ViewHolder>() {
    interface ItemClicker {
        fun onItemClick(index: Int)
    }

    companion object {
        private const val TYPE_ICON = 0
        private const val TYPE_DETAIL = 1
    }

    fun updateItem(index: Int) {
        notifyItemChanged(index)
    }

    override fun getItemCount(): Int = data.size

    override fun getItemViewType(position: Int): Int {
        return if (data[position].icon > 0)
            TYPE_ICON
        else
            TYPE_DETAIL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return if (viewType == TYPE_ICON)
            ViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_menu, null)
            )
        else ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_detail, null)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.setItem(data[position])
    }

    inner class ViewHolder(root: View) : RecyclerView.ViewHolder(root) {
        private val bgItem: View = root.findViewById(R.id.item_bg)
        private val tvTitle: TextView = root.findViewById(R.id.text_item)
        private val tvDes: TextView? = root.findViewById(R.id.des_item)
        private val ivIcon: ImageView? = root.findViewById(R.id.image_item)

        init {
            bgItem.setOnClickListener {
                clicker.onItemClick(layoutPosition)
            }
        }

        fun setItem(item: HelpItem) {
            tvTitle.text = item.title
            if (item.opened && item.content.isNotEmpty()) {
                tvDes?.isVisible = true
                tvDes?.text = item.content
                bgItem.setBackgroundResource(R.drawable.item_bg)
            } else {
                tvDes?.isVisible = false
                bgItem.setBackgroundResource(R.drawable.card_bg)
            }
            ivIcon?.setImageResource(item.icon)
        }
    }
}
