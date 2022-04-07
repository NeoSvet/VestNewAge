package ru.neosvet.vestnewage.helpers

import android.content.Context
import android.content.SharedPreferences
import ru.neosvet.utils.Const
import ru.neosvet.vestnewage.activity.BrowserActivity

class BrowserHelper(context: Context) {
    companion object {
        private const val THEME = "theme"
        private const val NOMENU = "nomenu"
        private const val NAVBUTTONS = "navb"
        private const val SCALE = "scale"
    }

    private val pref: SharedPreferences =
        context.getSharedPreferences(BrowserActivity::class.java.simpleName, Context.MODE_PRIVATE)
    private val editor: SharedPreferences.Editor = pref.edit()
    var isLightTheme: Boolean = pref.getInt(THEME, 0) == 0
    var zoom: Int = pref.getInt(SCALE, 0)
    var isNoMenu: Boolean = pref.getBoolean(NOMENU, false)
    var isNavButtons: Boolean = pref.getBoolean(NAVBUTTONS, true)
    var link: String = ""
    var search: String = ""
        private set
    var place: List<String> = listOf()
        private set
    var searchIndex: Int = -1
    var prog: Int = -1
    var isSearch: Boolean = false
        private set
    val request: String
        get() = place[searchIndex]

    fun save() {
        editor.putInt(THEME, if (isLightTheme) 0 else 1)
        editor.putInt(SCALE, zoom)
        editor.putBoolean(NOMENU, isNoMenu)
        editor.putBoolean(NAVBUTTONS, isNavButtons)
        editor.apply()
    }

    fun setSearchString(s: String) {
        search = s
        place = if (s.contains(Const.NN))
            s.split(Const.NN)
        else listOf(s)
        isSearch = true
        prog = 0
        searchIndex = 0
    }

    fun clearSearch() {
        search = ""
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