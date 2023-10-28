package ru.neosvet.vestnewage.view.list

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.view.list.holder.BaseHolder
import ru.neosvet.vestnewage.view.list.holder.BasicHolder
import ru.neosvet.vestnewage.view.list.holder.CheckHolder
import ru.neosvet.vestnewage.view.list.holder.InputHolder

class CabinetAdapter(
    private val host: Host
) : RecyclerView.Adapter<BasicHolder>() {
    interface Host {
        fun onClickItem(index: Int, item: BasicItem)
        fun onCheckItem(index: Int, value: Boolean)
        fun onTextItem(index: Int, value: String)
    }

    companion object {
        const val TYPE_INPUT = "i"
        const val TYPE_CHECK = "c"
        const val TYPE_TITLE = "t"
        private const val T_INPUT = 0
        private const val T_CHECK = 1
        private const val T_TEXT = 2
        private const val T_DETAIL = 3
        private const val T_TITLE = 4
    }

    private val data = mutableListOf<BasicItem>()
    private val input = mutableListOf<InputHolder>()
    private val check = mutableListOf<CheckHolder>()

    fun getText(index: Int) = if (index < input.size)
        input[index].text else ""

    fun getCheck(index: Int) = if (index < check.size)
        check[index].isChecked else false

    fun setCheck(index: Int, value: Boolean) {
        if (index >= check.size) return
        check[index].isChecked = value
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setItems(items: List<BasicItem>) {
        data.clear()
        data.addAll(items)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clear() {
        data.clear()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = data.size

    override fun getItemViewType(position: Int): Int = when {
        data[position].head == TYPE_INPUT -> T_INPUT
        data[position].head == TYPE_CHECK -> T_CHECK
        data[position].head == TYPE_TITLE -> T_TITLE
        data[position].des.isEmpty() -> T_TEXT
        else -> T_DETAIL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
        T_INPUT -> {
            val holder = InputHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_input, null),
                host::onTextItem
            )
            input.add(holder)
            holder
        }

        T_CHECK -> {
            val holder = CheckHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_checkbg, null),
                host::onCheckItem
            )
            check.add(holder)
            holder
        }

        T_TEXT -> BaseHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_text, null),
            host::onClickItem, null
        )

        T_TITLE -> BaseHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_title, null),
            host::onClickItem, null
        )

        else -> BaseHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_detail, null),
            host::onClickItem, null
        )
    }


    override fun onBindViewHolder(holder: BasicHolder, position: Int) {
        holder.setItem(data[position])
    }
}