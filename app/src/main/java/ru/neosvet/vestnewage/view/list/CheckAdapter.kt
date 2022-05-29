package ru.neosvet.vestnewage.view.list

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.CheckItem

class CheckAdapter(
    private val list: List<CheckItem>,
    private val checkByBg: Boolean = true,
    private val onChecked: (Int, Boolean) -> Int
) : RecyclerView.Adapter<CheckAdapter.ViewHolder>() {

    override fun getItemCount(): Int = list.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_check, null))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.setItem(list[position])
    }

    @SuppressLint("NotifyDataSetChanged")
    inner class ViewHolder(root: View) : RecyclerView.ViewHolder(root) {
        private val checkBox = root.findViewById(R.id.check_box) as CheckedTextView
        private var isSet = true

        init {
            if (checkByBg)
                checkBox.setBackgroundResource(R.drawable.menu_bg)
            else
                checkBox.setCheckMarkDrawable(R.drawable.check_selector)
            checkBox.setOnClickListener {
                var index = layoutPosition
                val isChecked = list[index].isChecked.not()
                list[index].isChecked = isChecked
                notifyItemChanged(index)
                index = onChecked.invoke(index, isChecked)
                if (index == -1)
                    notifyDataSetChanged()
                else if (index != layoutPosition)
                    notifyItemChanged(index)
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