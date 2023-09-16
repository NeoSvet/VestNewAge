package ru.neosvet.vestnewage.view.basic

import android.content.Context
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.viewmodel.state.BasicState

class StatusButton(
    private val context: Context,
    private val panel: View
) {
    private val anRotate = AnimationUtils.loadAnimation(context, R.anim.rotate)
    private val anHide = AnimationUtils.loadAnimation(context, R.anim.hide)
    private val tv: TextView = panel.findViewById(R.id.tvStatus)
    private val iv: ImageView = panel.findViewById(R.id.ivStatus)
    private val progBar: ProgressBar? = panel.findViewById(R.id.progStatus)
    private var error: BasicState.Error? = null
    private var stop = true
    var isVisible = false
        private set
    private var prog = false

    init {
        anRotate.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                if (!stop && isVisible) iv.startAnimation(anRotate)
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
        anHide.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                panel.visibility = View.GONE
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
    }

    fun setLoad(start: Boolean) {
        setError(null)
        stop = !start
        if (prog) {
            prog = false
            progBar?.progress = 0
            progBar?.visibility = View.GONE
        }
        clearAnimation()
        if (start) {
            loadText()
            isVisible = true
            iv.startAnimation(anRotate)
        } else {
            isVisible = false
            tv.text = context.getString(R.string.done)
            panel.startAnimation(anHide)
        }
    }

    fun setError(error: BasicState.Error?) {
        stop = true
        clearAnimation()
        if (error != null) {
            this.error = error
            if (prog) progBar?.isVisible = false
            tv.text = context.getString(R.string.crash)
            panel.setBackgroundResource(R.drawable.shape_red)
            iv.setImageResource(R.drawable.ic_close)
            isVisible = true
        } else {
            this.error = null
            panel.isVisible = false
            isVisible = false
            panel.setBackgroundResource(R.drawable.shape_norm)
            iv.setImageResource(R.drawable.ic_refresh)
        }
    }

    val isCrash: Boolean
        get() = error != null

    fun loadText() {
        tv.text = context.getString(R.string.load)
    }

    fun setClick(event: View.OnClickListener?) {
        panel.setOnClickListener(event)
    }

    fun onClick(): Boolean {
        error?.let {
            val builder = AlertDialog.Builder(context, R.style.NeoDialog)
                .setTitle(context.getString(R.string.error))
                .setMessage(it.message)
                .setPositiveButton(context.getString(R.string.send))
                { _, _ -> Lib.openInApps(Const.mailto + it.information, null) }
                .setNegativeButton(context.getString(android.R.string.cancel))
                { _, _ -> setError(null) }
                .setOnDismissListener { setError(null) }
            builder.create().show()
            setLoad(false)
            return true
        }
        return false
    }

    fun setProgress(percent: Int) {
        if (!prog) {
            clearAnimation()
            progBar?.isVisible = true
            prog = true
        }
        progBar?.progress = percent
    }

    private fun clearAnimation() {
        iv.clearAnimation()
        anHide.cancel()
        anRotate.cancel()
        panel.isVisible = true
    }
}