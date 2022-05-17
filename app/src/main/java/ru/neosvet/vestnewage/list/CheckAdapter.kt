package ru.neosvet.vestnewage.list

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R

class CheckAdapter(
    private val list: List<CheckItem>,
    private val checker: (Int, Boolean) -> Int
) : RecyclerView.Adapter<CheckAdapter.ViewHolder>() {

    override fun getItemCount(): Int = list.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_check, null))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.setItem(list[position])
    }

    @SuppressLint("NotifyDataSetChanged")
    inner class ViewHolder(root: View) : RecyclerView.ViewHolder(root) {
        private val checkBox: CheckBox = root.findViewById(R.id.check_box)
        private var isSet = true

        init {
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isSet) return@setOnCheckedChangeListener
                var index = layoutPosition
                list[index].isChecked = isChecked
                index = checker.invoke(index, isChecked)
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