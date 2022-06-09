package ru.neosvet.vestnewage.view.list

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.view.MotionEvent
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import kotlin.math.abs

class TouchHelper(
    private val onlyLimit: Boolean = false,
    private val events: (Events) -> Unit
) {
    enum class Events {
        SWIPE_LEFT, SWIPE_RIGHT, LIST_LIMIT
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
    private var distanceForSwipe: Int = 30
    private var limit: Int = 0

    fun attach(view: RecyclerView) {
        initTouchListener(view)
        if (onlyLimit) return
        distanceForSwipe = (30 * view.resources.displayMetrics.density).toInt()
        val helper = ItemTouchHelper(callback)
        helper.attachToRecyclerView(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initTouchListener(view: RecyclerView) {
        view.setOnTouchListener { _, event: MotionEvent ->
            initLimit(view)
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val max = view.computeVerticalScrollRange()
                    if (max > limit && max < view.height)
                        events.invoke(Events.LIST_LIMIT)
                }
                MotionEvent.ACTION_DOWN -> {
                    if (onlyLimit) return@setOnTouchListener false
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

    private fun initLimit(view: RecyclerView) {
        if (limit > 0) return
        val margin = view.context.resources.getDimension(R.dimen.content_margin_bottom).toInt()
        limit = view.height - margin
    }
}