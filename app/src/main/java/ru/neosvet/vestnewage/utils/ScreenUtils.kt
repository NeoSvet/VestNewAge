package ru.neosvet.vestnewage.utils

import android.app.Activity
import android.os.Build
import android.util.DisplayMetrics

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
        get() = if (isWide) 2 else 1

    @JvmStatic
    var width: Int = 0
        private set
    var height: Int = 0
        private set

    fun init(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val d = activity.windowManager.currentWindowMetrics
            width = d.bounds.width()
            height = d.bounds.height()
        } else {
            val metrics = DisplayMetrics()
            activity.windowManager.defaultDisplay.getMetrics(metrics)
            width = metrics.widthPixels
            height = metrics.heightPixels
        }
        val density = activity.resources.displayMetrics.density
        val w = (width / density).toInt()
        val h = (height / density).toInt()
        isWide = w > 415
        type = when {
            w > 1000 && w > h -> Type.TABLET_LAND
            h > 1000 -> Type.TABLET_PORT
            w > h -> Type.PHONE_LAND
            else -> Type.PHONE_PORT
        }
    }
}