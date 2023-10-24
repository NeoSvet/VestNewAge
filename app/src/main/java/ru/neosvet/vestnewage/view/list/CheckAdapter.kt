package ru.neosvet.vestnewage.view.list

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.CheckedTextView
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.CheckItem

class CheckAdapter(
    private val list: List<CheckItem>,
    private val checkByBg: Boolean = true,
    private val zeroMargin: Boolean = false,
    private val onChecked: (Int, Boolean) -> Int
) : RecyclerView.Adapter<CheckAdapter.ViewHolder>() {
    companion object {
        const val ACTION_NONE = -1
        const val ACTION_UPDATE_ALL = -2
    }

    var sizeCorrector: Byte = 0

    override fun getItemCount(): Int = list.size - sizeCorrector

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_check, null))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.setItem(list[position])
    }

    fun setChecked(index: Int, value: Boolean) {
        if (list[index].isChecked == value) return
        list[index].isChecked = value
        notifyItemChanged(index)
    }

    @SuppressLint("NotifyDataSetChanged")
    inner class ViewHolder(root: View) : RecyclerView.ViewHolder(root) {
        private val checkBox = root.findViewById(R.id.check_box) as CheckedTextView
        private var isSet = true

        init {
            if (checkByBg) checkBox.setBackgroundResource(R.drawable.menu_bg)
            else checkBox.setCheckMarkDrawable(R.drawable.check_selector)
            if (zeroMargin)
                checkBox.updateLayoutParams<MarginLayoutParams> {
                    updateMargins(0, 0, 0, 0)
                    width = -2
                }
            checkBox.setOnClickListener {
                var index = layoutPosition
                val isChecked = list[index].isChecked.not()
                list[index].isChecked = isChecked
                notifyItemChanged(index)
                index = onChecked.invoke(index, isChecked)
                when (index) {
                    ACTION_NONE -> {}
                    ACTION_UPDATE_ALL -> notifyDataSetChanged()
                    layoutPosition -> {
                        list[index].isChecked = !isChecked
                        notifyItemChanged(index)
                    }

                    else -> notifyItemChanged(index)
                }
            }
        }

        fun setItem(item: CheckItem) {
            isSet = true
            checkBox.text = item.title
            checkBox.isChecked = item.isChecked
            isSet = false
        }
    }
}