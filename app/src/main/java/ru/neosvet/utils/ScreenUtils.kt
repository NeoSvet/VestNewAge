package ru.neosvet.utils

import android.content.Context
import android.content.res.Configuration
import ru.neosvet.vestnewage.R

object ScreenUtils {
    enum class Type {
        PHONE_PORT, PHONE_LAND, TABLET_PORT, TABLET_LAND
    }

    @JvmStatic
    var type: Type = Type.PHONE_PORT
        private set

    @JvmStatic
    var isTablet7: Boolean = false
        private set

    @JvmStatic
    val isTabletLand: Boolean
        get() = type == Type.TABLET_LAND

    @JvmStatic
    val isTablet: Boolean
        get() = type == Type.TABLET_PORT
    val span: Int
        get() = when (type) {
            Type.PHONE_PORT -> 1
            Type.PHONE_LAND -> 2
            Type.TABLET_PORT -> 2
            Type.TABLET_LAND -> 2
        }

    fun init(context: Context) {
        type = when (context.resources.getInteger(R.integer.screen_mode)) {
            context.resources.getInteger(R.integer.screen_phone_port) ->
                Type.PHONE_PORT
            context.resources.getInteger(R.integer.screen_phone_land) ->
                Type.PHONE_LAND
            context.resources.getInteger(R.integer.screen_tablet_land) ->
                if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                    Type.TABLET_LAND else Type.TABLET_PORT
            context.resources.getInteger(R.integer.screen_tablet_port) ->
                Type.TABLET_PORT
            else ->
                Type.PHONE_PORT
        }
        isTablet7 = context.resources.getInteger(R.integer.tablet_7) == 1
        Lib.LOG("_____ TYPE=$type")
    }
}