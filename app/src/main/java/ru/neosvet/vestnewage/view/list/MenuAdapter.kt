package ru.neosvet.vestnewage.view.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.MenuItem

class MenuAdapter(
    private val clicker: (Int, MenuItem) -> Unit,
) : RecyclerView.Adapter<MenuAdapter.Holder>() {
    private val data = mutableListOf<MenuItem>()

    fun setItems(list: List<MenuItem>) {
        data.addAll(list)
    }

    fun addItem(image: Int, title: String) {
        data.add(MenuItem(image, title))
    }

    fun changeIcon(index: Int, image: Int) {
        data[index].image = image
        notifyItemChanged(index)
    }

    fun changeTitle(index: Int, title: String) {
        data[index].title = title
        notifyItemChanged(index)
    }

    fun select(index: Int) {
        for (i in data.indices) {
            if (data[i].isSelect) {
                data[i].isSelect = false
                notifyItemChanged(i)
                break
            }
        }
        data[index].isSelect = true
        notifyItemChanged(index)
    }

    override fun getItemCount(): Int = data.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_menu, null))


    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.setItem(data[position])
    }

    fun clear() {
        data.clear()
    }

    inner class Holder(
        private val root: View
    ) : RecyclerView.ViewHolder(root) {
        private val itemBg: View = root.findViewById(R.id.item_bg)
        private val tvTitle: TextView = root.findViewById(R.id.text_item)
        private val ivImage: ImageView = root.findViewById(R.id.image_item)

        fun setItem(item: MenuItem) {
            tvTitle.text = item.title
            ivImage.setImageResource(item.image)
            if (item.isSelect)
                itemBg.setBackgroundResource(R.drawable.selected)
            else
                itemBg.setBackgroundResource(R.drawable.item_bg)
            root.setOnClickListener {
                clicker.invoke(layoutPosition, item)
            }
        }
    }
}