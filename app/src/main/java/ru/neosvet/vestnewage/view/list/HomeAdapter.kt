package ru.neosvet.vestnewage.view.list

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.HomeItem
import ru.neosvet.vestnewage.data.Section
import ru.neosvet.vestnewage.view.list.holder.EmptyHolder
import ru.neosvet.vestnewage.view.list.holder.HomeEditHolder
import ru.neosvet.vestnewage.view.list.holder.HomeHolder
import ru.neosvet.vestnewage.view.list.holder.HomeMenuHolder
import java.util.Timer
import kotlin.concurrent.timer

class HomeAdapter(
    private val events: Events,
    val isEditor: Boolean,
    private val items: MutableList<HomeItem>,
    private val menu: MutableList<Section>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    interface Events {
        fun onItemClick(type: HomeItem.Type, action: HomeHolder.Action)
        fun onMenuClick(index: Int, section: Section)
        fun onItemMove(holder: RecyclerView.ViewHolder)
    }

    var loadingIndex = -1
        private set
    var isTall = false
    private var needTimer = !isEditor
    private var timer: Timer? = null
    private var view: RecyclerView? = null

    init {
        restoreTimer()
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        view = recyclerView
    }

    fun restoreTimer() {
        if (!needTimer) return
        val period = detectPeriod()
        if (needTimer)
            startTimer(period)
    }

    fun stopTimer() {
        timer?.cancel()
    }

    private fun clearTimer() {
        timer?.cancel()
        timer = null
    }

    private fun startTimer(period: Long) {
        timer = timer(initialDelay = period, period = period) {
            for (i in items.indices) {
                if (items[i].time > 0)
                    view?.post { notifyItemChanged(i) }
            }
            val p = detectPeriod()
            if (p != period) {
                clearTimer()
                if (needTimer) startTimer(p)
            }
        }
    }

    private fun detectPeriod(): Long {
        if (isEditor) return 0L
        var time = 0L
        items.forEach {
            if (it.time > time) time = it.time
        }
        if (time == 0L) {
            needTimer = false
            return 0L
        }
        needTimer = true
        return DateUnit.detectPeriod(time)
    }

    fun startLoading(index: Int) {
        if (isEditor) return
        finishLoading()
        loadingIndex = index
        notifyItemChanged(index)
    }

    fun finishLoading() {
        if (loadingIndex == -1 || isEditor) return
        val i = loadingIndex
        loadingIndex = -1
        notifyItemChanged(i)
        clearTimer()
        restoreTimer()
    }

    fun update(index: Int, item: HomeItem) {
        if (loadingIndex == index) loadingIndex = -1
        items[index] = item
        notifyItemChanged(index)
        if (item.time > 0L) {
            stopTimer()
            startTimer((10 * DateUnit.SEC_IN_MILLS).toLong())
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = items[position].type.value

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            HomeItem.Type.MENU.value -> HomeMenuHolder(
                LayoutInflater.from(parent.context).inflate(
                    if (isTall) R.layout.item_home_menu_tall else R.layout.item_home_menu, null
                ),
                events::onMenuClick
            )

            HomeItem.Type.DIV.value -> EmptyHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_home_div, null)
            )

            else -> if (isEditor)
                HomeEditHolder(
                    LayoutInflater.from(parent.context).inflate(
                        R.layout.item_home_edit, null
                    ), events::onItemMove
                ) else
                HomeHolder(
                    LayoutInflater.from(parent.context).inflate(
                        R.layout.item_home, null
                    ), events::onItemClick
                )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HomeHolder -> {
                holder.setItem(items[position])
                if (position == loadingIndex)
                    holder.startLoading()
            }

            is HomeEditHolder ->
                holder.setItem(items[position])

            is HomeMenuHolder -> {
                holder.setCell(0, menu[0])
                holder.setCell(1, menu[1])
                holder.setCell(2, menu[2])
                holder.setCell(3, menu[3])
            }
        }
    }

    fun moveUp(index: Int) {
        val item = items[index - 1]
        items.removeAt(index - 1)
        items.add(index, item)
        notifyItemMoved(index, index - 1)
    }

    fun moveDown(index: Int) {
        val item = items[index]
        items.removeAt(index)
        items.add(index + 1, item)
        notifyItemMoved(index, index + 1)
    }

    fun changeMenu(index: Int, section: Section) {
        menu[index] = section
        for (i in items.indices)
            if (items[i].type == HomeItem.Type.MENU) {
                notifyItemChanged(i)
                return
            }
    }
}