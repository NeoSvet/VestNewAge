package ru.neosvet.vestnewage.view.list

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.graphics.drawable.toBitmap
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class MarkersListHelper(
    private val events: Events,
    private val buttonParameters: SwipeButton.Parameters,
    private val leftButton: SwipeButton,
    private val rightButton: SwipeButton
) {
    private var exceptionIndex = -1
    private lateinit var recyclerView: RecyclerView

    interface Events {
        fun onMove(fromIndex: Int, toIndex: Int)
        fun onSwipe(index: Int, toLeft: Boolean)
    }

    private val callback = object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
    ) {
        private var swipedPos = -1

        override fun getDragDirs(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            if (viewHolder.layoutPosition == exceptionIndex) return 0
            return super.getDragDirs(recyclerView, viewHolder)
        }

        override fun getSwipeDirs(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            if (viewHolder.layoutPosition == exceptionIndex) return 0
            return super.getSwipeDirs(recyclerView, viewHolder)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val fromPos = viewHolder.layoutPosition
            val toPos = target.layoutPosition
            return fromPos != exceptionIndex && toPos != exceptionIndex
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
            swipedPos = -1
            events.onMove(fromPos, toPos)
            super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y)
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            if (swipedPos > -1) {
                recyclerView.adapter?.notifyItemChanged(swipedPos)
                swipedPos = -1
            }
            when (direction) {
                ItemTouchHelper.LEFT ->
                    events.onSwipe(viewHolder.layoutPosition, true)

                ItemTouchHelper.RIGHT ->
                    events.onSwipe(viewHolder.layoutPosition, false)
            }
        }

        override fun onChildDraw(
            c: Canvas, recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float,
            actionState: Int, isCurrentlyActive: Boolean
        ) {
            var x = dX
            if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                val itemView = viewHolder.itemView
                val width = itemView.width.toFloat()
                val size = if (width > 600) 300f else width / 2

                x = when {
                    x < 0 -> {
                        swipedPos = viewHolder.layoutPosition
                        drawButton(c, itemView, width - size, width)
                        -size
                    }

                    x > 0 -> {
                        swipedPos = viewHolder.layoutPosition
                        drawButton(c, itemView, 0f, size)
                        size
                    }

                    else -> {
                        swipedPos = -1
                        0f
                    }
                }
            }

            super.onChildDraw(
                c, recyclerView, viewHolder, x, dY, actionState, isCurrentlyActive
            )
        }
    }

    private fun drawButton(c: Canvas, itemView: View, a: Float, b: Float) {
        val button = if (a == 0f) rightButton else leftButton
        button.onDraw(
            c, RectF(a, itemView.top.toFloat(), b, itemView.bottom.toFloat()), buttonParameters
        )
    }

    private val helper = ItemTouchHelper(callback)

    fun attach(view: RecyclerView, exceptionIndex: Int) {
        this.exceptionIndex = exceptionIndex
        recyclerView = view
        helper.attachToRecyclerView(view)
    }

    fun detach() {
        helper.attachToRecyclerView(null)
    }

    fun startMove(holder: RecyclerView.ViewHolder) {
        helper.startDrag(holder)
    }
}

class SwipeButton(
    private val icon: Drawable?,
    private val color: Int
) {
    data class Parameters(
        val size: Int,
        val padding: Int,
        val radius: Float
    )

    private var image: Bitmap? = null

    fun onDraw(c: Canvas, rect: RectF, param: Parameters) {
        if (image == null) image = icon?.toBitmap(param.size, param.size)
        val p = Paint()
        p.color = color
        val n = param.padding + param.padding
        val left = if (rect.left == 0f) rect.left + n
        else rect.left - n
        val right = if (rect.left == 0f) rect.right + n
        else rect.right - n
        val newRect = RectF(
            left, rect.top, right, rect.bottom - param.padding
        )
        if (param.radius == 0f) c.drawRect(newRect, p)
        else c.drawRoundRect(newRect, param.radius, param.radius, p)
        val r = Rect()
        val x = newRect.width() / 2f - r.width() / 2f - r.left
        val y = newRect.height() / 2f + r.height() / 2f - r.bottom

        image?.let {
            c.drawBitmap(
                it, newRect.left + x - it.width / 2,
                newRect.top + y - it.height / 2, p
            )
        }
    }
}