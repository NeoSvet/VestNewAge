package ru.neosvet.vestnewage.view.list.holder

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
import ru.neosvet.vestnewage.view.basic.convertToDpi
import ru.neosvet.vestnewage.view.basic.fromDpi

interface HomeHolder {
    fun setItem(item: HomeItem)
    sealed class Action {
        data object TITLE : Action()
        data object REFRESH : Action()
        data class SUBTITLE(val link: String) : Action()
    }
}

class HomeBaseHolder(
    root: View,
    private val clicker: (HomeItem.Type, HomeHolder.Action) -> Unit
) : RecyclerView.ViewHolder(root), HomeHolder {
    private val ivIcon: ImageView = root.findViewById(R.id.icon)
    private val tvLine1: TextView = root.findViewById(R.id.line1)
    private val tvLine2: TextView = root.findViewById(R.id.line2)
    private val tvLine3: TextView = root.findViewById(R.id.line3)
    private val ivRefresh: ImageView = root.findViewById(R.id.refresh)
    private val ivRefreshBg: ImageView = root.findViewById(R.id.refresh_bg)

    override fun setItem(item: HomeItem) {
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
                left = tvLine1.context.convertToDpi(50)
            )
            ivIcon.setImageDrawable(ContextCompat.getDrawable(ivIcon.context, icon))
        }
        tvLine1.text = item.lines[0]
        tvLine2.text = item.timeString ?: item.lines[1]
        tvLine3.text = item.lines[2]
        tvLine1.setOnClickListener {
            clicker.invoke(item.type, HomeHolder.Action.TITLE)
        }
        tvLine3.setOnClickListener {
            clicker.invoke(item.type, HomeHolder.Action.SUBTITLE(item.link))
        }
        if (item.hasRefresh) {
            ivRefresh.isVisible = true
            ivRefreshBg.isVisible = true
            ivRefresh.setOnClickListener {
                clicker.invoke(item.type, HomeHolder.Action.REFRESH)
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
) : RecyclerView.ViewHolder(root), HomeHolder {
    private val mIds = listOf(
        listOf(R.id.item1, R.id.icon1, R.id.title1),
        listOf(R.id.item2, R.id.icon2, R.id.title2),
        listOf(R.id.item3, R.id.icon3, R.id.title3),
        listOf(R.id.item4, R.id.icon4, R.id.title4)
    )

    fun setCell(index: Int, section: Section) {
        val point = HomeHelper.getSectionPoint(section, false)
        val mId = mIds[index]
        val iv: ImageView = root.findViewById(mId[1])
        val tv: TextView = root.findViewById(mId[2])
        iv.setImageDrawable(ContextCompat.getDrawable(iv.context, point.x))
        tv.text = tv.context.getString(point.y)
        root.findViewById<View>(mId[0]).setOnClickListener {
            clicker.invoke(index, section)
        }
    }

    override fun setItem(item: HomeItem) {}
}

class HomeEditHolder(
    root: View,
    mover: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.ViewHolder(root), HomeHolder {
    private val ivIcon: ImageView = root.findViewById(R.id.icon)
    private val tvTitle: TextView = root.findViewById(R.id.title)

    init {
        initTouch(root.findViewById(R.id.move), mover)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initTouch(ivMove: View, mover: (RecyclerView.ViewHolder) -> Unit) {
        ivMove.setOnTouchListener { _, event: MotionEvent ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                mover.invoke(this)
                return@setOnTouchListener false
            }
            return@setOnTouchListener true
        }
    }

    override fun setItem(item: HomeItem) {
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
    }
}