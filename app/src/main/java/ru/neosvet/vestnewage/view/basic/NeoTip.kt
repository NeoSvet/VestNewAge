package ru.neosvet.vestnewage.view.basic

import android.content.Context
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R

class NeoTip(context: Context, private val view: View) {
    var isShow = false
        private set
    var autoHide = true
    private val anShow: Animation = AnimationUtils.loadAnimation(context, R.anim.show)
    private val anHide: Animation = AnimationUtils.loadAnimation(context, R.anim.hide)
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        anHide.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                view.visibility = View.GONE
                isShow = false
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
    }

    fun show() {
        if (isShow) return
        isShow = true
        view.visibility = View.VISIBLE
        view.startAnimation(anShow)
        if (autoHide) {
            job?.cancel()
            job = scope.launch {
                delay(2500)
                if (isShow) view.post {
                    view.startAnimation(anHide)
                }
            }
        }
    }

    fun hide() {
        if (!isShow) return
        job?.cancel()
        view.clearAnimation()
        view.visibility = View.GONE
        isShow = false
    }

    fun hideAnim() {
        if (!isShow) return
        job?.cancel()
        view.clearAnimation()
        view.startAnimation(anHide)
    }
}