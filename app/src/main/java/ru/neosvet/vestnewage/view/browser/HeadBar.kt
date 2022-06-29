package ru.neosvet.vestnewage.view.browser

import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import ru.neosvet.vestnewage.R
import kotlin.math.absoluteValue

class HeadBar(
    private val mainView: View,
    private val additionViews: List<View>,
    private val onClick: () -> Unit
) {
    private enum class State {
        EXPANDED, COLLAPSED, GONE
    }

    private var time: Long = 0
    private val goneH = 1
    private var collapsedH = 0
    private var expandedH = 0
    private var state = State.EXPANDED
    private var isTop = true
    private var isBlocked = false

    val isExpanded: Boolean
        get() = state == State.EXPANDED && isBlocked.not()

    init {
        mainView.setOnClickListener {
            if (state == State.EXPANDED)
                onClick.invoke()
            else
                changeHeight(expandedH)
        }
        mainView.setOnLongClickListener {
            onClick.invoke()
            return@setOnLongClickListener true
        }
        mainView.post {
            expandedH = mainView.height
            collapsedH = mainView.context.resources.getDimension(R.dimen.head_bar_height).toInt()
        }
    }

    private fun changeEnd() {
        time = System.currentTimeMillis()
        state = when (mainView.height) {
            goneH -> {
                hideViews()
                State.GONE
            }
            expandedH ->
                State.EXPANDED
            else ->
                State.COLLAPSED
        }
        if (mainView is ImageView) {
            if (state == State.EXPANDED)
                mainView.scaleType = ImageView.ScaleType.FIT_XY
            else if (state == State.COLLAPSED)
                mainView.scaleType = ImageView.ScaleType.CENTER_CROP
        }
        unblocked()
    }

    private fun changeHeight(h: Int) {
        if (System.currentTimeMillis() - time < 500) return
        blocked()
        val i = mainView.height
        val v = h - i
        mainView.clearAnimation()
        val a: Animation = object : Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
                if (interpolatedTime == 1f) {
                    setHeight(h)
                    changeEnd()
                } else
                    setHeight(i + (v * interpolatedTime).toInt())
            }
        }
        a.duration = 225
        mainView.startAnimation(a)
    }

    private fun setHeight(h: Int) {
        mainView.updateLayoutParams<ViewGroup.LayoutParams> {
            height = h
        }
    }

    fun onScrollHost(y: Int, oldY: Int): Boolean {
        if (isBlocked) return false
        if ((y - oldY).absoluteValue < 10) return false
        isTop = y > oldY
        when (state) {
            State.EXPANDED ->
                if (isTop && y > 100) changeHeight(collapsedH)
            State.COLLAPSED ->
                if (isTop && y > 200) changeHeight(goneH)
            State.GONE -> if (isTop.not()) {
                showViews()
                changeHeight(collapsedH)
            }
        }
        return true
    }

    private fun showViews() {
        mainView.isVisible = true
        for (v in additionViews)
            v.isVisible = true
    }

    private fun hideViews() {
        mainView.isVisible = false
        for (v in additionViews)
            v.isVisible = false
    }

    fun expanded() {
        if (isBlocked) return
        showViews()
        changeHeight(expandedH)
        time = System.currentTimeMillis()
    }

    fun hide() {
        changeHeight(goneH)
        blocked()
    }

    fun show() {
        showViews()
        changeHeight(collapsedH)
    }

    fun blocked() {
        isBlocked = true
    }

    fun unblocked() {
        isBlocked = false
    }
}