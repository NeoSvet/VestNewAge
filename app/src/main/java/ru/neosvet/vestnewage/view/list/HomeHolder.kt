package ru.neosvet.vestnewage.view.list

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.HomeItem
import ru.neosvet.vestnewage.data.Section
import ru.neosvet.vestnewage.helper.HomeHelper
import ru.neosvet.vestnewage.view.basic.convertDpi
import ru.neosvet.vestnewage.view.basic.fromDpi

class HomeHolder(
    root: View,
    private val clicker: (HomeItem.Type, Action) -> Unit
) : RecyclerView.ViewHolder(root) {
    enum class Action {
        TITLE, SUBTITLE, REFRESH
    }

    private val ivIcon: ImageView = root.findViewById(R.id.icon)
    private val tvLine1: TextView = root.findViewById(R.id.line1)
    private val tvLine2: TextView = root.findViewById(R.id.line2)
    private val tvLine3: TextView = root.findViewById(R.id.line3)
    private val ivRefresh: ImageView = root.findViewById(R.id.refresh)
    private val ivRefreshBg: ImageView = root.findViewById(R.id.refresh_bg)

    fun setItem(item: HomeItem) {
        val icon = when (item.type) {
            HomeItem.Type.SUMMARY -> R.drawable.ic_summary
            HomeItem.Type.NEWS -> R.drawable.ic_site
            HomeItem.Type.CALENDAR -> R.drawable.ic_calendar
            HomeItem.Type.JOURNAL -> R.drawable.ic_journal
            else -> -1
        }
        if (icon == -1) {
            ivIcon.isVisible = false
            tvLine1.updatePadding(
                left = tvLine1.context.fromDpi(R.dimen.def_indent)
            )
        } else {
            ivIcon.isVisible = true
            tvLine1.updatePadding(
                left = tvLine1.context.convertDpi(50)
            )
            ivIcon.setImageDrawable(ContextCompat.getDrawable(ivIcon.context, icon))
        }
        tvLine1.text = item.lines[0]
        tvLine2.text = item.lines[1]
        tvLine3.text = item.lines[2]
        tvLine1.setOnClickListener {
            clicker.invoke(item.type, Action.TITLE)
        }
        tvLine3.setOnClickListener {
            clicker.invoke(item.type, Action.SUBTITLE)
        }
        if (item.hasRefresh) {
            ivRefresh.isVisible = true
            ivRefreshBg.isVisible = true
            ivRefresh.setOnClickListener {
                clicker.invoke(item.type, Action.REFRESH)
            }
        } else {
            ivRefresh.isVisible = false
            ivRefreshBg.isVisible = false
        }
    }

    fun startLoading() {
        ivRefresh.startAnimation(anRotate)
    }

    private val anRotate: Animation by lazy {
        initAnimation()
    }

    private fun initAnimation(): Animation {
        val animation = AnimationUtils.loadAnimation(App.context, R.anim.rotate)
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                ivRefresh.startAnimation(anRotate)
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
        return animation
    }
}

class HomeMenuHolder(
    private val root: View,
    private val clicker: (Int, Section) -> Unit
) : RecyclerView.ViewHolder(root) {
    private val mIds = listOf(
        listOf(R.id.item1, R.id.icon1, R.id.title1),
        listOf(R.id.item2, R.id.icon2, R.id.title2),
        listOf(R.id.item3, R.id.icon3, R.id.title3),
        listOf(R.id.item4, R.id.icon4, R.id.title4)
    )

    fun setCell(index: Int, section: Section) {
        val point = HomeHelper.getSectionPoint(section, false)
        val mId = mIds[index]
        val iv = root.findViewById(mId[1]) as ImageView
        val tv = root.findViewById(mId[2]) as TextView
        iv.setImageDrawable(ContextCompat.getDrawable(iv.context, point.x))
        tv.text = tv.context.getString(point.y)
        root.findViewById<View>(mId[0]).setOnClickListener {
            clicker.invoke(index, section)
        }
    }
}

class EmptyHolder(root: View) : RecyclerView.ViewHolder(root)

class HomeEditHolder(
    root: View,
    private val mover: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.ViewHolder(root) {
    private val ivIcon: ImageView = root.findViewById(R.id.icon)
    private val tvTitle: TextView = root.findViewById(R.id.title)
    private val ivMove: View = root.findViewById(R.id.move)

    @SuppressLint("ClickableViewAccessibility")
    fun setItem(item: HomeItem) {
        val icon = when (item.type) {
            HomeItem.Type.SUMMARY -> R.drawable.ic_summary
            HomeItem.Type.NEWS -> R.drawable.ic_site
            HomeItem.Type.CALENDAR -> R.drawable.ic_calendar
            HomeItem.Type.JOURNAL -> R.drawable.ic_journal
            else -> -1
        }
        if (icon == -1) {
            ivIcon.isVisible = false
        } else {
            ivIcon.isVisible = true
            ivIcon.setImageDrawable(ContextCompat.getDrawable(ivIcon.context, icon))
        }
        tvTitle.text = item.lines[0]

        ivMove.setOnTouchListener { _, event: MotionEvent ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                mover.invoke(this)
                return@setOnTouchListener false
            }
            return@setOnTouchListener true
        }
    }
}