package ru.neosvet.vestnewage.helper

import android.content.Context
import android.content.SharedPreferences
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.utils.Const

class BookHelper {
    companion object {
        const val TAG = "Book"
        private var loadedOtkr: Boolean? = null
    }

    private val pref: SharedPreferences by lazy {
        App.context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
    }
    var poemsDays: Int = 16801 //Январь 2016
        private set
    var epistlesDays: Int = 12631 //Август 2004
        private set

    fun isLoadedOtkr(): Boolean {
        if (loadedOtkr == null)
            loadedOtkr = pref.getBoolean(Const.OTKR, false)
        return loadedOtkr!!
    }

    fun setLoadedOtkr(value: Boolean) {
        loadedOtkr = value
        val editor = pref.edit()
        editor.putBoolean(Const.OTKR, value)
        editor.apply()
    }

    fun saveDates(poemsDays: Int, epistlesDays: Int) {
        val editor = pref.edit()
        editor.putInt(Const.POEMS, poemsDays)
        editor.putInt(Const.EPISTLES, epistlesDays)
        editor.apply()
    }

    fun loadDates() {
        val d = DateUnit.initToday()
        d.day = 1
        try {
            poemsDays = pref.getInt(Const.POEMS, d.timeInDays)
            d.month = 9
            d.year = 2016
            epistlesDays = pref.getInt(Const.EPISTLES, d.timeInDays)
        } catch (e: Exception) { //if old version
            poemsDays = (pref.getLong(Const.POEMS, 0) /
                    DateUnit.SEC_IN_MILLS / DateUnit.DAY_IN_SEC).toInt()
            epistlesDays = (pref.getLong(Const.EPISTLES, 0) /
                    DateUnit.SEC_IN_MILLS / DateUnit.DAY_IN_SEC).toInt()
        }
    }
}