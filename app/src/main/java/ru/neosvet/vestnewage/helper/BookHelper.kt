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
        const val MIN_DAYS_POEMS = 16832 //Февраль 2016
        const val MIN_DAYS_OLD_BOOK = 12631 //Август 2004
        const val MIN_DAYS_NEW_BOOK = 16801 //Январь 2016
        const val MAX_DAYS_BOOK = 17045 //Сентябрь 2016
    }

    private val pref: SharedPreferences by lazy {
        App.context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
    }
    var poemsDays: Int = MIN_DAYS_POEMS
        private set
    var epistlesDays: Int = MIN_DAYS_NEW_BOOK
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
        try {
            val d = DateUnit.initToday().apply { day = 1 }
            poemsDays = pref.getInt(Const.POEMS, d.timeInDays)
            epistlesDays = pref.getInt(Const.EPISTLES, MAX_DAYS_BOOK)
        } catch (e: Exception) { //if old version
            poemsDays = (pref.getLong(Const.POEMS, 0) /
                    DateUnit.SEC_IN_MILLS / DateUnit.DAY_IN_SEC).toInt()
            epistlesDays = (pref.getLong(Const.EPISTLES, 0) /
                    DateUnit.SEC_IN_MILLS / DateUnit.DAY_IN_SEC).toInt()
        }
        if (poemsDays < MIN_DAYS_POEMS)
            poemsDays = MIN_DAYS_POEMS
        if (isLoadedOtkr()) {
            if (epistlesDays < MIN_DAYS_OLD_BOOK)
                epistlesDays = MIN_DAYS_OLD_BOOK
        } else if (epistlesDays < MIN_DAYS_NEW_BOOK)
            epistlesDays = MIN_DAYS_NEW_BOOK
    }
}