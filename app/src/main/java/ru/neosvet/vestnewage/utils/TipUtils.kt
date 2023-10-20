package ru.neosvet.vestnewage.utils

import android.content.Context
import android.content.SharedPreferences
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.view.activity.TipActivity

class TipUtils {
    enum class Vertical {
        TOP, BOTTOM
    }

    enum class Horizontal {
        LEFT, CENTER, RIGHT
    }

    data class Unit(
        val message: String,
        val imgId: Int,
        val alignH: Horizontal,
        val alignV: Vertical,
        val addArrow: Boolean
    )

    enum class Type {
        MAIN_STAR, CALENDAR, BROWSER_PANEL, BROWSER_FULLSCREEN, SEARCH
    }

    companion object {
        const val TAG = "tip"
        private val pref: SharedPreferences by lazy {
            App.context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        }

        @JvmStatic
        fun showTipIfNeed(name: Type) {
            if (pref.getBoolean(name.toString(), true)) {
                TipActivity.showTip(name)
                val editor = pref.edit()
                editor.putBoolean(name.toString(), false)
                editor.apply()
            }
        }

        fun getUnit(type: Type): Unit =
            when (type) {
                Type.MAIN_STAR -> Unit(
                    message = App.context.getString(R.string.tip_main),
                    imgId = R.drawable.tip_main,
                    alignH = Horizontal.RIGHT,
                    alignV = Vertical.BOTTOM,
                    addArrow = true
                )

                Type.CALENDAR -> Unit(
                    message = App.context.getString(R.string.tip_calendar),
                    imgId = R.drawable.tip_calendar,
                    alignH = Horizontal.CENTER,
                    alignV = Vertical.TOP,
                    addArrow = true
                )

                Type.BROWSER_PANEL -> Unit(
                    message = App.context.getString(R.string.tip_browser),
                    imgId = R.drawable.tip_browser,
                    alignH = Horizontal.CENTER,
                    alignV = Vertical.BOTTOM,
                    addArrow = false
                )

                Type.BROWSER_FULLSCREEN -> Unit(
                    message = App.context.getString(R.string.tip_browser2),
                    imgId = R.drawable.tip_browser2,
                    alignH = Horizontal.LEFT,
                    alignV = Vertical.TOP,
                    addArrow = true
                )

                Type.SEARCH -> Unit(
                    message = App.context.getString(R.string.tip_search),
                    imgId = R.drawable.tip_search,
                    alignH = Horizontal.LEFT,
                    alignV = Vertical.TOP,
                    addArrow = true
                )
            }

        fun offAll() {
            val editor = pref.edit()
            TipUtils.Type.values().forEach {
                editor.putBoolean(it.toString(), false)
            }
            editor.apply()
        }
    }
}