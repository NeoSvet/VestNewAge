package ru.neosvet.vestnewage.view.list

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.CalendarItem

class CalendarAdapter(
    private val clicker: (View, CalendarItem) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.ViewHolder>() {
    private var data = listOf<CalendarItem>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, pos: Int) {
        holder.setItem(data[pos])
    }

    override fun getItemCount(): Int = data.size

    @SuppressLint("NotifyDataSetChanged")
    fun setItems(items: List<CalendarItem>) {
        data = items
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val num: TextView = itemView.findViewById(R.id.cell_num)

        @SuppressLint("ClickableViewAccessibility")
        fun setItem(item: CalendarItem) {
            num.text = item.text
            num.setTextColor(item.color)
            itemView.background = item.background
            if (item.isBold)
                num.setTypeface(null, Typeface.BOLD)
            else num.setTypeface(null, Typeface.NORMAL)
            itemView.setOnTouchListener { view: View, event: MotionEvent ->
                if (event.action == MotionEvent.ACTION_UP) clicker.invoke(view, item)
                false
            }
        }
    }
}