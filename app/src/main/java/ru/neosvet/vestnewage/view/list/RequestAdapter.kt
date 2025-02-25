package ru.neosvet.vestnewage.view.list

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Files
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileReader
import java.io.FileWriter

class RequestAdapter(
    context: Context,
    private val selectItem: (String) -> Unit
) : RecyclerView.Adapter<RequestAdapter.Holder>() {
    companion object {
        const val LIMIT = 21
        fun getType(item: BasicItem): Type {
            if (item.link.length == 5) return Type.LOAD_MONTH
            if (item.title.contains(Const.HTML)) return Type.LOAD_PAGE
            return Type.NORMAL
        }
    }

    enum class Type {
        NORMAL, LOAD_MONTH, LOAD_PAGE
    }

    var adapter: ArrayAdapter<String>
        private set

    private val items = mutableListOf<String>()

    init {
        val f = Files.slash(Const.SEARCH)
        adapter = ArrayAdapter<String>(context, R.layout.spinner_item)
        if (f.exists() && items.isEmpty()) {
            val br = BufferedReader(FileReader(f))
            br.forEachLine {
                adapter.add(it)
                items.add(it)
            }
            br.close()
        }
    }

    override fun getItemCount(): Int = items.size + 1

    override fun getItemViewType(position: Int): Int {
        return position
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestAdapter.Holder {
        return if (viewType == 0)
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_menu, null))
        else
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_close, null))
    }

    override fun onBindViewHolder(holder: RequestAdapter.Holder, position: Int) {
        holder.setItem(position)
    }

    inner class Holder(private val root: View) : RecyclerView.ViewHolder(root) {
        fun setItem(index: Int) {
            val tvText: TextView = root.findViewById(R.id.text_item)
            if (index == 0) {
                tvText.text = root.context.getString(R.string.clear_list)
                root.setBackgroundResource(R.drawable.press)
                root.setOnClickListener { clear() }
                val ivImage: ImageView = root.findViewById(R.id.image_item)
                ivImage.setImageResource(R.drawable.ic_clear)
                return
            }
            val p = index - 1
            tvText.text = items[p]
            tvText.setOnClickListener {
                selectItem.invoke(items[p])
            }
            root.findViewById<View>(R.id.close_item).setOnClickListener {
                removeItem(p)
            }
        }
    }

    private fun removeItem(index: Int) {
        adapter.remove(items[index])
        items.removeAt(index)
        notifyItemRemoved(index + 1)
    }

    fun save() {
        val f = Files.slash(Const.SEARCH)
        f.delete()
        val bw = BufferedWriter(FileWriter(f))
        items.forEach {
            bw.write(it + Const.N)
        }
        bw.close()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clear() {
        adapter.clear()
        items.clear()
        notifyDataSetChanged()
        val f = Files.slash(Const.SEARCH)
        if (f.exists()) f.delete()
    }

    fun add(request: String) {
        val i = items.indexOf(request)
        if (i == 0) return
        if (i > -1) {
            items.removeAt(i)
            adapter.remove(request)
            notifyItemRemoved(i + 1)
        }
        if (itemCount == LIMIT) {
            adapter.remove(items[LIMIT - 2])
            items.removeAt(LIMIT - 2)
            notifyItemRemoved(LIMIT - 1)
        }
        items.add(0, request)
        adapter.insert(request, 0)
        notifyItemInserted(0)
    }
}