package ru.neosvet.vestnewage.view.list

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.view.list.TabAdapter.Holder

class TabAdapter(
    btnPrev: View?, btnNext: View?,
    private val isHorizontal: Boolean,
    private val clicker: (Int) -> Unit
) : RecyclerView.Adapter<Holder>() {
    private var recyclerView: RecyclerView? = null
    private val items = mutableListOf<String>()

    var isBlocked = false

    var selected = 0
        private set

    init {
        btnPrev?.setOnClickListener {
            if (isBlocked) return@setOnClickListener
            val i = selected
            if (i > 0) select(i - 1)
            else select(itemCount - 1)
            clicker.invoke(selected)
        }
        btnNext?.setOnClickListener {
            if (isBlocked) return@setOnClickListener
            val i = selected + 1
            if (i == itemCount) select(0)
            else select(i)
            clicker.invoke(selected)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setItems(list: List<String>) {
        if (list.size == items.size) return
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun select(index: Int) {
        if (selected == index) return
        val i = selected
        selected = index
        notifyItemChanged(i)
        notifyItemChanged(index)
        recyclerView?.smoothScrollToPosition(index)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
        val context = recyclerView.context
        if (isHorizontal) {
            recyclerView.layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            recyclerView.addItemDecoration(
                DividerItemDecoration(context, DividerItemDecoration.HORIZONTAL)
            )
        } else {
            recyclerView.layoutManager = GridLayoutManager(context, 1)
            recyclerView.addItemDecoration(
                DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val layout = LayoutInflater.from(parent.context).inflate(R.layout.item_tab, null)
        return Holder(layout, clicker)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.setItem(items[position])
    }

    inner class Holder(
        root: View,
        private val clicker: (Int) -> Unit
    ) : RecyclerView.ViewHolder(root) {
        private val tv: TextView = root.findViewById(android.R.id.text1)

        fun setItem(caption: String) {
            tv.text = caption
            val index = absoluteAdapterPosition
            if (index == selected)
                tv.background = ContextCompat.getDrawable(tv.context, R.drawable.selected_tab)
            else tv.setBackgroundColor(
                ContextCompat.getColor(
                    tv.context,
                    android.R.color.transparent
                )
            )
            tv.setOnClickListener {
                if (index == selected || isBlocked) return@setOnClickListener
                select(index)
                clicker.invoke(index)
            }
        }
    }

}