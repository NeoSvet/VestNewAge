package ru.neosvet.vestnewage.view.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R

class DateAdapter(
    private val minYear: Int,
    private val click: View.OnClickListener
) : RecyclerView.Adapter<DateAdapter.ViewHolder>() {
    private val data = mutableListOf<String>()
    var minPos = -1
        set(value) {
            val i = field
            field = value
            notifyItemChanged(i)
            if (value > -1) {
                notifyItemChanged(value)
                if (selected < value)
                    selected = value
            }
        }
    var maxPos = 12
        set(value) {
            val i = field
            field = value
            notifyItemChanged(i)
            if (value < 12) {
                notifyItemChanged(value)
                if (selected > value)
                    selected = value
            }
        }
    var selected = 0
        set(value) {
            if (field != value && value in minPos..maxPos) {
                val i = field
                field = value
                notifyItemChanged(i)
                notifyItemChanged(field)
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_date, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, pos: Int) {
        holder.tv.text = data[pos]
        when (pos) {
            maxPos, minPos ->
                if (pos == selected) holder.bg.setBackgroundResource(R.drawable.cell_bg_all)
                else holder.bg.setBackgroundResource(R.drawable.cell_bg_epi)

            selected ->
                holder.bg.setBackgroundResource(R.drawable.cell_bg_poe)

            in (maxPos + 1) until minYear -> {
                holder.bg.setBackgroundResource(R.drawable.cell_bg_none)
                holder.bg.isEnabled = false
            }

            else ->
                holder.bg.setBackgroundResource(R.drawable.cell_bg_none)
        }
        holder.tv.tag = pos
        holder.tv.setOnClickListener(click)
    }

    override fun getItemCount() = data.size

    fun addItem(s: String) {
        data.add(s)
    }

    fun clear() {
        minPos = -1
        maxPos = 12
        data.clear()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val bg: View = itemView.findViewById(R.id.cell_bg)
        val tv: TextView = itemView.findViewById(R.id.cell_tv)
    }
}