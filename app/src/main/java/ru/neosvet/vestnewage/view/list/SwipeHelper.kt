package ru.neosvet.vestnewage.view.list

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.view.MotionEvent
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.utils.Lib
import kotlin.math.abs


class SwipeHelper(
    val events: (Events) -> Unit
) {
    enum class Events {
        SWIPE_LEFT, SWIPE_RIGHT
    }

    private val callback = object : ItemTouchHelper.SimpleCallback(
        0,
        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean = false

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            Lib.LOG("onSwiped")
            when (direction) {
                ItemTouchHelper.LEFT ->
                    events.invoke(Events.SWIPE_LEFT)
                ItemTouchHelper.RIGHT ->
                    events.invoke(Events.SWIPE_RIGHT)
            }
        }

        override fun onChildDraw(
            c: Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean
        ) {
//            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }
    private var isDown = false
    private var x = 0
    private var y = 0
    var distanceForSwipe: Int = 30
        private set

    fun attach(view: RecyclerView) {
        distanceForSwipe = (30 * view.resources.displayMetrics.density).toInt()
        val helper = ItemTouchHelper(callback)
        helper.attachToRecyclerView(view)
        initTouchListener(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initTouchListener(view: RecyclerView) {
        view.setOnTouchListener { _, event: MotionEvent ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isDown = true
                    x = event.getX(0).toInt()
                    y = event.getY(0).toInt()
                }
                MotionEvent.ACTION_UP -> {
                    if (isDown.not()) return@setOnTouchListener false
                    isDown = false
                    val x2 = event.getX(0).toInt()
                    val y2 = event.getY(0).toInt()
                    val r = abs(x - x2)
                    val r2 = abs(y - y2)
                    if (r > distanceForSwipe && r > r2) {
                        if (x > x2) // next
                            events.invoke(Events.SWIPE_LEFT)
                        else if (x < x2) // prev
                            events.invoke(Events.SWIPE_RIGHT)
                    }
                }
            }
            false
        }
    }
}