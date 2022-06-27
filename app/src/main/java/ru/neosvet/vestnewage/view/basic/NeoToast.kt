package ru.neosvet.vestnewage.view.basic

import android.widget.TextView
import androidx.core.view.isVisible
import kotlinx.coroutines.*

class NeoToast(
    private val view: TextView,
    private val hideEvent: (() -> Unit)?
) {
    var isShow = false
        private set
    var autoHide = true
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun destroy() {
        job?.cancel()
    }

    fun show(msg: String) {
        if (isShow) hide()
        view.text = msg
        isShow = true
        view.isVisible = true
        view.alpha = 0f
        view.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
        if (autoHide)
            runAutoHide()
    }

    private fun runAutoHide() {
        job?.cancel()
        job = scope.launch {
            delay(2700)
            if (isShow) view.post {
                hideAnimated()
            }
        }
    }

    fun hide(): Boolean {
        if (!isShow) return false
        job?.cancel()
        hideEvent?.invoke()
        view.clearAnimation()
        view.isVisible = false
        isShow = false
        return true
    }

    fun hideAnimated() {
        if (!hide()) return
        view.isVisible = true
        view.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction { view.isVisible = false }
            .start()
    }
}