package ru.neosvet.vestnewage.utils

import android.app.Activity
import android.graphics.Point
import android.os.Build
import ru.neosvet.vestnewage.R

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
        type = when (activity.resources.getInteger(R.integer.screen_mode)) {
            activity.resources.getInteger(R.integer.screen_phone_port) ->
                Type.PHONE_PORT
            activity.resources.getInteger(R.integer.screen_phone_land) ->
                Type.PHONE_LAND
            activity.resources.getInteger(R.integer.screen_tablet_land) ->
                Type.TABLET_LAND
            activity.resources.getInteger(R.integer.screen_tablet_port) ->
                Type.TABLET_PORT
            else ->
                Type.PHONE_PORT
        }
        if (type != Type.PHONE_LAND) {
            isWide = isTabletLand
            return
        }
        val width: Int
        val height: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val d = activity.windowManager.currentWindowMetrics
            width = d.bounds.width()
            height = d.bounds.height()
        } else {
            val p = Point(0, 0)
            activity.windowManager.defaultDisplay.getRealSize(p)
            width = p.x
            height = p.y
        }
        val ratio = width / height.toFloat()
        isWide = ratio > 1.8f
    }
}