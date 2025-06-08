package ru.neosvet.vestnewage.utils

import android.graphics.Insets
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import ru.neosvet.vestnewage.R

@RequiresApi(Build.VERSION_CODES.S)
class InsetsUtils(
    private val view: View,
    private val activity: AppCompatActivity
) {
    var applyInsets: ((Insets) -> Boolean)? = null
    var isSideNavBar: Boolean = false
        private set
    var navBar: ImageView? = null
        private set
    private var isDone = false

    private fun run(insets: Insets) {
        view.post {
            setNavBar(insets)
            isDone = applyInsets?.invoke(insets) ?: false
        }
    }

    private fun setNavBar(insets: Insets) {
        val root = activity.findViewById<ViewGroup>(R.id.root) ?: return
        if (insets.right > 0 || insets.left > 0) {
            isSideNavBar = true
            navBar = ImageView(activity).apply {
                setBackgroundColor(ContextCompat.getColor(activity, R.color.navigationBarColor))
                val w = if (insets.right > insets.left) insets.right else insets.left
                val p = LayoutParams(w, LayoutParams.MATCH_PARENT)
                setLayoutParams(p)
                root.addView(this)
                if (w == insets.right) {
                    if (root is CoordinatorLayout) updateLayoutParams<CoordinatorLayout.LayoutParams> {
                        gravity = Gravity.END
                    }
                    else updateLayoutParams<ConstraintLayout.LayoutParams> {
                        endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                    }
                }
            }
        } else if (activity.findViewById<View>(R.id.bottomBar) == null) {
            navBar = ImageView(activity).apply {
                setBackgroundColor(ContextCompat.getColor(activity, R.color.navigationBarColor))
                val p = LayoutParams(LayoutParams.MATCH_PARENT, insets.bottom)
                setLayoutParams(p)
                root.addView(this)
                updateLayoutParams<CoordinatorLayout.LayoutParams> {
                    gravity = Gravity.BOTTOM
                }
            }
        }
    }

    fun init(window: Window) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        window.decorView.setOnApplyWindowInsetsListener { view, insets ->
            val systemInsets = insets.getInsets(WindowInsets.Type.systemBars())
            //println("---- WindowInsets top=${systemInsets.top} bottom=${systemInsets.bottom} left=${systemInsets.left} right=${systemInsets.right}")
            if (!isDone) run(systemInsets)
            insets
        }
    }

}