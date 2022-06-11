package ru.neosvet.vestnewage.view.basic

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View
import android.view.ViewPropertyAnimator
import ru.neosvet.vestnewage.R

class BottomAnim(private val view: View) {
    private val addition = view.context.resources.getDimension(R.dimen.content_margin_bottom)
    private var anim: ViewPropertyAnimator? = null
    private var animListener = object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            anim = null
        }
    }

    fun hide() {
        if (view.translationY > 0) return
        val y = view.height.toFloat() + addition
        anim = view
            .animate()
            .translationY(y)
            .setDuration(175)
            .setListener(animListener)
    }

    fun show() {
        if (view.translationY == 0f) return
        anim = view
            .animate()
            .translationY(0f)
            .setDuration(225)
            .setListener(animListener)
    }
}