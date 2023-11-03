package ru.neosvet.vestnewage.helper

import android.content.Context
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.view.basic.convertDpi

class BrowserHelper {
    companion object {
        const val TAG = "Browser"
        private const val THEME = "theme"
        private const val NAVBUTTONS = "navb"
        private const val MITITOP = "minitop"
        private const val AUTORETURN = "autoreturn"
        private const val NUMPAR = "numpar"
        private const val SCALE = "scale"
    }

    // Options
    var isLightTheme = true
    var zoom = 0
    var isNavButton = true
    var isMiniTop = false
    var isAutoReturn = false
    var isNumPar = false

    // Status
    var place = listOf<String>()
    var placeIndex = -1
    var isSearch = false

    val request: String
        get() = place[placeIndex].trimEnd()

    fun load(context: Context) {
        val pref = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        isLightTheme = pref.getInt(THEME, 0) == 0
        zoom = pref.getInt(SCALE, 0)
        isNavButton = pref.getBoolean(NAVBUTTONS, true)
        isMiniTop = pref.getBoolean(MITITOP, false)
        isAutoReturn = pref.getBoolean(AUTORETURN, false)
        isNumPar = pref.getBoolean(NUMPAR, false)
        if (zoom < 10) zoom = context.convertDpi(100)
    }

    fun save(context: Context) {
        if (zoom == 0) return
        val pref = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        val editor = pref.edit()
        editor.putInt(THEME, if (isLightTheme) 0 else 1)
        editor.putInt(SCALE, zoom)
        editor.putBoolean(NAVBUTTONS, isNavButton)
        editor.putBoolean(MITITOP, isMiniTop)
        editor.putBoolean(AUTORETURN, isAutoReturn)
        editor.putBoolean(NUMPAR, isNumPar)
        editor.apply()
    }

    fun setSearchString(s: String) {
        place = if (s.contains(Const.NN))
            s.split(Const.NN)
        else listOf(s)
        isSearch = true
        placeIndex = 0
    }

    fun clearSearch() {
        place = listOf()
        placeIndex = -1
        isSearch = false
    }

    fun prevSearch(): Boolean {
        if (placeIndex > -1) {
            if (place.size < 2) return false
            if (--placeIndex == -1)
                placeIndex = place.size - 1
            return true
        }
        return false
    }

    fun nextSearch(): Boolean {
        if (placeIndex > -1) {
            if (place.size < 2) return false
            if (++placeIndex == place.size)
                placeIndex = 0
            return true
        }
        return false
    }
}