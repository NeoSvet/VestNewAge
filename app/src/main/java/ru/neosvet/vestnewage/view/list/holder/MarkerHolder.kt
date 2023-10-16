package ru.neosvet.vestnewage.view.list.holder

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.MarkerItem

abstract class MarkerHolder(root: View) : RecyclerView.ViewHolder(root) {
    interface Events {
        fun onClick(index: Int)
        fun onLongClick(index: Int)
        fun onItemMove(holder: RecyclerView.ViewHolder)
    }

    abstract fun setItem(item: MarkerItem)
}

class MarkerBaseHolder(
    root: View,
    private val events: Events
) : MarkerHolder(root) {
    private val tvTitle: TextView = root.findViewById(R.id.text_item)
    private val tvDes: TextView? = root.findViewById(R.id.des_item)
    private val itemBg: View = root.findViewById(R.id.item_bg)

    init {
        itemBg.setOnClickListener {
            events.onClick(layoutPosition)
        }
        itemBg.setOnLongClickListener {
            events.onLongClick(layoutPosition)
            true
        }
    }

    override fun setItem(item: MarkerItem) {
        tvTitle.text = item.title
        tvDes?.text = if (item.text.isEmpty()) item.des
        else String.format("%s:\n%s", item.des, item.text)
        itemBg.setBackgroundResource(R.drawable.item_bg)
    }
}

class MarkerEditHolder(
    root: View,
    private val events: Events
) : MarkerHolder(root) {
    private val tvTitle: TextView = root.findViewById(R.id.text_item)
    private val tvDes: TextView? = root.findViewById(R.id.des_item)
    private val ivMove: View = root.findViewById(R.id.move)

    init {
        initTouch()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initTouch() {
        ivMove.setOnTouchListener { _, event: MotionEvent ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                events.onItemMove(this)
                return@setOnTouchListener false
            }
            return@setOnTouchListener true
        }
    }

    override fun setItem(item: MarkerItem) {
        tvTitle.text = item.title
        if (tvDes == null) ivMove.isVisible = layoutPosition > 0
        else tvDes.text = item.des
    }
}