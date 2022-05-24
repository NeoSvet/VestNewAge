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
    var katrenDays: Int = 16801 //Январь 2016
        private set
    var poslaniyaDays: Int = 12631 //Август 2004
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

    fun saveDates(katrenDays: Int, poslaniyaDays: Int) {
        val editor = pref.edit()
        editor.putInt(Const.KATRENY, katrenDays)
        editor.putInt(Const.POSLANIYA, poslaniyaDays)
        editor.apply()
    }

    fun loadDates() {
        val d = DateUnit.initToday()
        d.day = 1
        try {
            katrenDays = pref.getInt(Const.KATRENY, d.timeInDays)
            d.month = 9
            d.year = 2016
            poslaniyaDays = pref.getInt(Const.POSLANIYA, d.timeInDays)
        } catch (e: Exception) { //if old version
            katrenDays = (pref.getLong(Const.KATRENY, 0) /
                    DateUnit.SEC_IN_MILLS / DateUnit.DAY_IN_SEC).toInt()
            poslaniyaDays = (pref.getLong(Const.POSLANIYA, 0) /
                    DateUnit.SEC_IN_MILLS / DateUnit.DAY_IN_SEC).toInt()
        }
    }
}