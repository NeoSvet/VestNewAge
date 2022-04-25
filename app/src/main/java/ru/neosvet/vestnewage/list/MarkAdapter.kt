package ru.neosvet.vestnewage.list

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.model.MarkersModel

class MarkAdapter(context: Context, private val model: MarkersModel) : BaseAdapter() {
    private val inflater: LayoutInflater =
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    private val data: List<MarkItem>
        get() = model.list

    override fun getCount(): Int = data.size

    override fun getItem(i: Int): MarkItem = data[i]

    override fun getItemId(i: Int): Long = 0

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var tv: TextView
        val view: View
        if (data[position].des.isEmpty()) {
            view = inflater.inflate(R.layout.item_list, null)
        } else {
            view = inflater.inflate(R.layout.item_detail, null)
            tv = view.findViewById(R.id.des_item)
            tv.text = data[position].des
        }
        tv = view.findViewById(R.id.text_item)
        tv.text = data[position].title
        val item_bg = view.findViewById<View>(R.id.item_bg)
        if (data[position].isSelect)
            item_bg.setBackgroundResource(R.drawable.select_item_bg)
        else
            item_bg.setBackgroundResource(R.drawable.item_bg)
        return view
    }
}