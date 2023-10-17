package ru.neosvet.vestnewage.view.list.holder

import android.view.View
import android.widget.TextView
import androidx.core.text.isDigitsOnly
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.view.list.BasicAdapter

class ListHolder(
    private val root: View,
    private val clicker: (Int, BasicItem) -> Unit,
    private val longClicker: ((Int, BasicItem) -> Boolean)?
) : BasicHolder(root) {
    private val tvTitle: TextView = root.findViewById(R.id.text_item)
    private val tvTime: TextView = root.findViewById(R.id.time_item)
    private val tvDes: TextView = root.findViewById(R.id.des_item)
    private val rvList: RecyclerView = root.findViewById(R.id.list_item)

    init {
        val item: View = root.findViewById(R.id.item_bg)
        item.setBackgroundResource(R.drawable.item_bg)
    }

    override fun setItem(item: BasicItem) {
        tvTitle.text = if (item.title.contains("<"))
            item.title.fromHTML else item.title

        val i = item.des.indexOf("$")
        val des = if (i > -1) {
            tvTime.isVisible = true
            tvTime.text = item.des.substring(0, i)
            item.des.substring(i + 1)
        } else {
            tvTime.isVisible = false
            item.des
        }

        tvDes.text = if (des.contains("<"))
            des.fromHTML else des

        root.setOnClickListener {
            clicker.invoke(layoutPosition, item)
        }
        longClicker?.let { event ->
            root.setOnLongClickListener {
                event.invoke(layoutPosition, item)
            }
        }

        val adapter = BasicAdapter(clicker, null)
        val list = mutableListOf<BasicItem>()
        val ctx = root.context
        item.headsAndLinks().forEach {
            val link = if (it.second.isDigitsOnly())
                Urls.TelegramUrl + it.second
            else it.second
            if (!link.contains(Const.DOCTRINE)) {
                val p = BasicItem(it.first, link)
                p.des = when {
                    link.contains(".jpg") -> ctx.getString(R.string.image)
                    link.indexOf("mailto") == 0 -> ctx.getString(R.string.mail)
                    link.indexOf(Urls.TelegramUrl) == 0 -> {
                        p.title = ctx.getString(R.string.open_post)
                        "[Telegram]"
                    }

                    p.title.contains("(YouTube)") -> {
                        p.title = p.title.replace("(YouTube)", "")
                        "[YouTube]"
                    }

                    else -> ctx.getString(R.string.webpage)
                }
                list.add(p)
            }
        }
        adapter.setItems(list)
        rvList.adapter = adapter
    }
}