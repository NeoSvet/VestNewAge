package ru.neosvet.vestnewage.view.basic

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import androidx.core.view.isVisible
import com.lukelorusso.verticalseekbar.VerticalSeekBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NeoScrollBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : VerticalSeekBar(context, attrs, defStyleAttr) {
    private var isRightScroll = false
    private var isEndScrollAnim = true
    private var isMoveScrollBar = true

    var value: Int
        get() = maxValue - progress
        set(value) {
            isMoveScrollBar = false
            progress = maxValue - value
        }

    @SuppressLint("ClickableViewAccessibility")
    fun init(max: Int, scope: CoroutineScope, onChange: (Int) -> Unit) {
        isVisible = true
        moveScrollBar(false)
        maxValue = max
        progress = max
        setOnReleaseListener { v ->
            onChange.invoke(max - v)
            scope.launch {
                delay(900)
                moveScrollBar(true)
            }
        }
        setOnProgressChangeListener { v ->
            if (isMoveScrollBar)
                moveScrollBar(false)
            else
                isMoveScrollBar = true
        }
        scope.launch {
            delay(600)
            moveScrollBar(true)
        }
    }

    private fun moveScrollBar(isRight: Boolean) {
        if (isRightScroll == isRight || isEndScrollAnim.not()) return
        isRightScroll = isRight
        val d = 35 * resources.displayMetrics.density
        isEndScrollAnim = false
        animate()
            .withEndAction { isEndScrollAnim = true }
            .setDuration(300)
            .translationXBy(if (isRight) d else -d)
            .start()
    }
}