package ru.neosvet.vestnewage.view.browser

import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.Transformation
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import kotlin.math.absoluteValue

class HeadBar(
    private val mainView: View,
    distanceForHide: Int,
    private val additionViews: List<View>,
    private val onClick: () -> Unit
) {
    private enum class State {
        EXPANDED, COLLAPSED, GONE
    }

    private val collapseDistance = distanceForHide
    private val goneDistance = distanceForHide * 2
    private var time: Long = 0
    private val goneH = 1
    private var collapsedH = 0
    private var expandedH = 0
    private var state = State.EXPANDED
    private var isTop = true
    private var isNotExpandable = false
    private var isBlocked = false
    val isHided: Boolean
        get() = state == State.GONE

    val isExpanded: Boolean
        get() = state == State.EXPANDED && isBlocked.not()

    private val anHide = AnimationUtils.loadAnimation(App.context, R.anim.hide)
    private val anShow = AnimationUtils.loadAnimation(App.context, R.anim.show)
    private var isFristAnim = true

    init {
        mainView.setOnClickListener {
            if (state == State.EXPANDED)
                onClick.invoke()
            else
                changeHeight(expandedH)
        }
        initAnim()
        mainView.setOnLongClickListener {
            onClick.invoke()
            return@setOnLongClickListener true
        }
        mainView.post {
            expandedH = mainView.height
            collapsedH =
                mainView.context.resources.getDimension(R.dimen.head_collapsed_height).toInt()
        }
    }

    private fun initAnim() {
        anShow.duration = 300
        anHide.duration = 300
        anShow.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                if (isFristAnim.not()) return
                isFristAnim = false
                for (v in additionViews)
                    v.isVisible = v.tag == null
            }

            override fun onAnimationEnd(animation: Animation) {}
            override fun onAnimationRepeat(animation: Animation) {}
        })
        anHide.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                if (isFristAnim.not()) return
                isFristAnim = false
                for (v in additionViews)
                    v.isVisible = false
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
    }

    private fun changeEnd() {
        time = System.currentTimeMillis()
        state = when (mainView.height) {
            goneH -> {
                mainView.isVisible = false
                State.GONE
            }

            expandedH ->
                State.EXPANDED

            else ->
                State.COLLAPSED
        }
        if (state == State.EXPANDED) {
            isFristAnim = true
            for (v in additionViews)
                if (v.tag == null) v.startAnimation(anShow)
        }
        unblocked()
    }

    private fun changeHeight(h: Int) {
        if (System.currentTimeMillis() - time < 500) return
        blocked()
        mainView.clearAnimation()
        if (h != expandedH) {
            isFristAnim = true
            for (v in additionViews)
                if (v.tag == null) v.startAnimation(anHide)
        }
        val i = mainView.height
        val v = h - i
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
                if (isTop && y > collapseDistance) changeHeight(collapsedH)

            State.COLLAPSED ->
                if (isTop && y > goneDistance) changeHeight(goneH)

            State.GONE -> if (isTop.not()) {
                mainView.isVisible = true
                changeHeight(collapsedH)
            }
        }
        return true
    }

    fun expanded() {
        if (isBlocked) return
        mainView.isVisible = true
        if (isNotExpandable)
            changeHeight(collapsedH)
        else
            changeHeight(expandedH)
        time = System.currentTimeMillis()
    }

    fun hide() {
        changeHeight(goneH)
        blocked()
    }

    fun show() {
        mainView.isVisible = true
        changeHeight(collapsedH)
    }

    fun blocked() {
        isBlocked = true
    }

    fun unblocked() {
        isBlocked = false
    }

    fun setExpandable(isNot: Boolean) {
        if (isNot && state == State.EXPANDED)
            changeHeight(collapsedH)
        isNotExpandable = isNot
    }
}