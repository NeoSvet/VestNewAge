package ru.neosvet.vestnewage.utils

import android.app.Activity
import android.graphics.Point
import android.os.Build

object ScreenUtils {
    enum class Type {
        PHONE_PORT, PHONE_LAND, TABLET_PORT, TABLET_LAND
    }

    @JvmStatic
    var type: Type = Type.PHONE_PORT
        private set

    @JvmStatic
    var isWide: Boolean = false
        private set

    @JvmStatic
    val isLand: Boolean
        get() = type == Type.PHONE_LAND || isTabletLand

    @JvmStatic
    val isTabletLand: Boolean
        get() = type == Type.TABLET_LAND

    @JvmStatic
    val isTablet: Boolean
        get() = type >= Type.TABLET_PORT
    val span: Int
        get() = if (type == Type.PHONE_PORT)
            1 else 2

    fun init(activity: Activity) {
        val width: Int
        val height: Int
        val density = activity.resources.displayMetrics.density
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val d = activity.windowManager.currentWindowMetrics
            width = (d.bounds.width() / density).toInt()
            height = (d.bounds.height() / density).toInt()
        } else {
            val p = Point(0, 0)
            activity.windowManager.defaultDisplay.getRealSize(p)
            width = (p.x / density).toInt()
            height = (p.y / density).toInt()
        }
        isWide = width > 600
        type = when {
            width > 1000 && width > height -> Type.TABLET_LAND
            height > 1000 -> Type.TABLET_PORT
            width > height -> Type.PHONE_LAND
            else -> Type.PHONE_PORT
        }
    }
}