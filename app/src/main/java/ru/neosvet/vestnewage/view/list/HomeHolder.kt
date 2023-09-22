package ru.neosvet.vestnewage.view.list

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
        if (item.lines.size == 2) {
            tvLine2.isVisible = false
            tvLine3.text = item.lines[1]
        } else {
            tvLine2.isVisible = true
            tvLine2.text = item.lines[1]
            tvLine3.text = item.lines[2]
        }
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
    root: View,
    private val clicker: (Section) -> Unit
) : RecyclerView.ViewHolder(root) {

    init {
        root.findViewById<View>(R.id.book).setOnClickListener {
            clicker.invoke(Section.BOOK)
        }
        root.findViewById<View>(R.id.markers).setOnClickListener {
            clicker.invoke(Section.MARKERS)
        }
        root.findViewById<View>(R.id.edit).setOnClickListener {
            clicker.invoke(Section.MENU)
        }
        root.findViewById<View>(R.id.settings).setOnClickListener {
            clicker.invoke(Section.SETTINGS)
        }
    }
}