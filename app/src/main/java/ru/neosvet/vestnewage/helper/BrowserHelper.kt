package ru.neosvet.vestnewage.helper

import android.content.Context
import ru.neosvet.vestnewage.utils.Const

class BrowserHelper(context: Context) {
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
    private val pref = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
    var isLightTheme: Boolean = pref.getInt(THEME, 0) == 0
    var zoom: Int = pref.getInt(SCALE, 0)
    var isNavButton: Boolean = pref.getBoolean(NAVBUTTONS, true)
    var isMiniTop: Boolean = pref.getBoolean(MITITOP, false)
    var isAutoReturn: Boolean = pref.getBoolean(AUTORETURN, false)
    var isNumPar: Boolean = pref.getBoolean(NUMPAR, false)

    // Status
    var place = listOf<String>()
    var searchIndex = -1
    var prog = -1
    var isSearch = false
    var position = 0f
    var isFullScreen = false

    val request: String
        get() = place[searchIndex].trimEnd()

    init {
        if (zoom < 10)
            zoom = (context.resources.displayMetrics.density * 100).toInt()
    }

    fun save() {
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
        prog = 0
        searchIndex = 0
    }

    fun clearSearch() {
        place = listOf()
        searchIndex = -1
        isSearch = false
    }

    fun prevSearch(): Boolean {
        if (searchIndex > -1) {
            if (--searchIndex == -1)
                searchIndex = place.size - 1
            return true
        }
        return false
    }

    fun nextSearch(): Boolean {
        if (searchIndex > -1) {
            if (++searchIndex == place.size)
                searchIndex = 0
            return true
        }
        return false
    }

    fun upProg() {
        prog++
    }

    fun downProg() {
        prog--
    }
}