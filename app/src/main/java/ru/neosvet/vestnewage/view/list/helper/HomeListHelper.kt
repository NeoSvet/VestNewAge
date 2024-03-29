package ru.neosvet.vestnewage.view.list.helper

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.view.list.holder.HomeHolder
import ru.neosvet.vestnewage.view.list.holder.SimpleHolder

class HomeListHelper(
    private val events: (Int, Boolean) -> Unit
) {
    private val callback = object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
    ) {
        override fun getDragDirs(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            if (viewHolder is HomeHolder)
                return super.getDragDirs(recyclerView, viewHolder)
            return 0
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            if (viewHolder !is HomeHolder) return false
            if (target is SimpleHolder) return false
            return true
        }

        override fun onMoved(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            fromPos: Int,
            target: RecyclerView.ViewHolder,
            toPos: Int,
            x: Int,
            y: Int
        ) {
            events.invoke(fromPos, fromPos > toPos)
            super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y)
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        }

    }
    val helper = ItemTouchHelper(callback)

    fun attach(view: RecyclerView) {
        helper.attachToRecyclerView(view)
    }

    fun detach() {
        helper.attachToRecyclerView(null)
    }

    fun startMove(holder: RecyclerView.ViewHolder) {
        helper.startDrag(holder)
    }
}