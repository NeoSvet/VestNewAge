package ru.neosvet.vestnewage.view.list

import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.HomeItem
import ru.neosvet.vestnewage.data.Section

class HomeHolder(
    root: View,
    private val clicker: (HomeItem.Type, Action) -> Unit
) : RecyclerView.ViewHolder(root) {
    enum class Action {
        TITLE, SUBTITLE, REFRESH
    }

    private val tvLine1: TextView = root.findViewById(R.id.line1)
    private val tvLine2: TextView = root.findViewById(R.id.line2)
    private val tvLine3: TextView = root.findViewById(R.id.line3)
    private val ivRefresh: ImageView = root.findViewById(R.id.refresh)
    private val ivRefreshBg: ImageView = root.findViewById(R.id.refresh_bg)

    fun setItem(item: HomeItem) {
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