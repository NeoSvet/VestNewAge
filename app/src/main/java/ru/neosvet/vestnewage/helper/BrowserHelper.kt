package ru.neosvet.vestnewage.helper

import android.content.Context
import androidx.core.content.edit
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.view.basic.convertToDpi

class BrowserHelper {
    companion object {
        const val TAG = "Browser"
        private const val THEME = "theme"
        private const val NAVBUTTONS = "navb"
        private const val MITITOP = "minitop"
        private const val AUTORETURN = "autoreturn"
        private const val NUMPAR = "numpar"
        private const val SCALE = "scale"
        private const val SHOW_REACTION = "reaction"
        var showReaction = false
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
        get() = if (placeIndex == -1 || place.isEmpty()) "" else place[placeIndex].trimEnd()

    fun load(context: Context) {
        val pref = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        isLightTheme = pref.getInt(THEME, 0) == 0
        zoom = pref.getInt(SCALE, 0)
        isNavButton = pref.getBoolean(NAVBUTTONS, true)
        isMiniTop = pref.getBoolean(MITITOP, false)
        isAutoReturn = pref.getBoolean(AUTORETURN, false)
        isNumPar = pref.getBoolean(NUMPAR, false)
        showReaction = pref.getBoolean(SHOW_REACTION, false)
        if (zoom < 10) zoom = context.convertToDpi(100)
    }

    fun save(context: Context) {
        if (zoom == 0) return
        val pref = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        pref.edit {
            putInt(THEME, if (isLightTheme) 0 else 1)
            putInt(SCALE, zoom)
            putBoolean(NAVBUTTONS, isNavButton)
            putBoolean(MITITOP, isMiniTop)
            putBoolean(AUTORETURN, isAutoReturn)
            putBoolean(NUMPAR, isNumPar)
            putBoolean(SHOW_REACTION, showReaction)
        }
    }

    fun setSearchString(s: String) {
        place = when {
            s.contains(Const.NN) -> s.split(Const.NN)
            s.contains(Const.N) -> s.split(Const.N)
            else -> listOf(s)
        }
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