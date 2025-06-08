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
import ru.neosvet.vestnewage.view.basic.convertToDpi
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
    var collapsedH = 0
    private var expandedH = 0
    private var minOffsetY = 10
    private var state = State.EXPANDED
    private var isTop = true
    private var isNotExpandable = false
    var isBlocked = false
        private set

    var switchGone: (() -> Unit)? = null
    val isHided: Boolean
        get() = state == State.GONE
    val isExpanded: Boolean
        get() = state == State.EXPANDED && isBlocked.not()

    private val anHide = AnimationUtils.loadAnimation(App.context, R.anim.hide)
    private val anShow = AnimationUtils.loadAnimation(App.context, R.anim.show)
    private var isFirstAnim = true

    init {
        mainView.setOnClickListener {
            if (state == State.EXPANDED)
                onClick.invoke()
            else changeHeight(expandedH)
        }
        initAnim()
        mainView.setOnLongClickListener {
            onClick.invoke()
            return@setOnLongClickListener true
        }
        mainView.post {
            expandedH = mainView.height
            collapsedH = mainView.context.convertToDpi(50)
        }
        minOffsetY = mainView.context.convertToDpi(minOffsetY)
    }

    private fun initAnim() {
        anShow.duration = 300
        anHide.duration = 300
        anShow.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                if (isFirstAnim.not()) return
                isFirstAnim = false
                for (v in additionViews)
                    v.isVisible = v.tag == null
            }

            override fun onAnimationEnd(animation: Animation) {}
            override fun onAnimationRepeat(animation: Animation) {}
        })
        anHide.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                if (isFirstAnim.not()) return
                isFirstAnim = false
                for (v in additionViews)
                    v.isVisible = false
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
    }

    private fun changeEnd() {
        state = when (mainView.height) {
            goneH -> {
                if (switchGone == null) mainView.isVisible = false
                State.GONE
            }

            expandedH -> State.EXPANDED
            else -> {
                if (isHided) switchGone?.invoke()
                State.COLLAPSED
            }
        }
        if (state == State.EXPANDED) {
            isFirstAnim = true
            for (v in additionViews) {
                if (v.tag == null && !v.isVisible)
                    v.startAnimation(anShow)
            }
        }
        unblocked()
    }

    private fun changeHeight(h: Int) {
        blocked()
        mainView.clearAnimation()
        if (h != expandedH) {
            isFirstAnim = true
            for (v in additionViews) {
                if (v.tag == null && v.isVisible)
                    v.startAnimation(anHide)
            }
        }
        if (h == goneH && switchGone != null) {
            switchGone?.invoke()
            state = State.GONE
            unblocked()
            return
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
        if (System.currentTimeMillis() - time < 500) return false
        if ((y - oldY).absoluteValue < minOffsetY) return false
        time = System.currentTimeMillis()
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
        if (isNotExpandable) changeHeight(collapsedH)
        else changeHeight(expandedH)
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