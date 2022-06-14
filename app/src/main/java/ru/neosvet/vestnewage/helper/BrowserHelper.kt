package ru.neosvet.vestnewage.helper

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.utils.Const

class BrowserHelper(context: Context) {
    companion object {
        const val TAG = "Browser"
        private const val THEME = "theme"
        private const val NAVBUTTONS = "navb"
        private const val SCALE = "scale"
    }

    private val pref = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
    private val editor: SharedPreferences.Editor = pref.edit()
    var isLightTheme: Boolean = pref.getInt(THEME, 0) == 0
    var zoom: Int = pref.getInt(SCALE, 0)
    var isNavButton: Boolean = pref.getBoolean(NAVBUTTONS, true)
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
        editor.putBoolean(NAVBUTTONS, isNavButton)
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

    fun sharePage(context: Context, title: String) {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "text/plain"
        var s: String = title
        if (s.length > 9)
            s = s.substring(9) + " (" +
                    context.getString(R.string.from) +
                    " " + s.substring(0, 8) + ")"
        shareIntent.putExtra(Intent.EXTRA_TEXT, s + Const.N + NeoClient.SITE + link)
        val intent = Intent.createChooser(shareIntent, context.getString(R.string.share))
        context.startActivity(intent)
    }
}