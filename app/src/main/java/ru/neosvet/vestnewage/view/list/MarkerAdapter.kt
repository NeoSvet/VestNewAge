package ru.neosvet.vestnewage.view.list

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.MarkerItem

class MarkerAdapter(
    private val events: MarkerHolder.Events,
    val isEditor: Boolean,
    val items: List<MarkerItem>
) : RecyclerView.Adapter<MarkerHolder>() {
    companion object {
        private const val TYPE_SIMPLE = 0
        private const val TYPE_DETAIL = 1
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return if (items[position].des.isEmpty())
            TYPE_SIMPLE else TYPE_DETAIL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MarkerHolder =
        when (viewType) {
            TYPE_SIMPLE -> if (isEditor) EditHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_list_edit, null), events
            ) else Holder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_list, null), events
            )

            else -> if (isEditor) EditHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_detail_edit, null), events
            ) else Holder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_detail, null), events
            )
        }

    override fun onBindViewHolder(holder: MarkerHolder, position: Int) {
        holder.setItem(items[position])
    }

}