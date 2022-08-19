package ru.neosvet.vestnewage.view.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.MarkerItem
import ru.neosvet.vestnewage.viewmodel.MarkersToiler

class MarkerAdapter(
    private val toiler: MarkersToiler,
    private val longClicker: ((Int) -> Boolean)
) : RecyclerView.Adapter<MarkerAdapter.ViewHolder>() {
    companion object {
        private const val TYPE_SIMPLE = 0
        private const val TYPE_DETAIL = 1
    }

    private val data: List<MarkerItem>
        get() = toiler.list

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

    inner class ViewHolder(root: View) : RecyclerView.ViewHolder(root) {
        private val tvTitle: TextView = root.findViewById(R.id.text_item)
        private val tvDes: TextView? = root.findViewById(R.id.des_item)
        private val itemBg: View = root.findViewById(R.id.item_bg)
        private var index: Int = 0

        init {
            itemBg.setOnClickListener {
                toiler.run {
                    val oldSel = iSel
                    onClick(index)
                    if (oldSel > -1)
                        notifyItemChanged(oldSel)
                    if (iSel > -1)
                        notifyItemChanged(index)
                }
            }
            itemBg.setOnLongClickListener {
                if (toiler.iSel == -1)
                    longClicker.invoke(index)
                else false
            }
        }

        fun setItem(index: Int) {
            this.index = index
            tvTitle.text = data[index].title
            tvDes?.text = data[index].des
            if (index == toiler.iSel)
                itemBg.setBackgroundResource(R.drawable.select_item_bg)
            else
                itemBg.setBackgroundResource(R.drawable.item_bg)
        }
    }
}