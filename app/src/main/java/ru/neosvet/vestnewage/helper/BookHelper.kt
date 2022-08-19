package ru.neosvet.vestnewage.helper

import android.content.Context
import android.content.SharedPreferences
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.utils.Const

class BookHelper {
    companion object {
        const val TAG = "Book"
    }

    private val pref: SharedPreferences by lazy {
        App.context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
    }
    var poemsDays: Int = DateHelper.MIN_DAYS_POEMS
        private set
    var epistlesDays: Int = DateHelper.MIN_DAYS_NEW_BOOK
        private set

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
            epistlesDays = pref.getInt(Const.EPISTLES, DateHelper.MAX_DAYS_BOOK)
        } catch (e: Exception) { //if old version
            poemsDays = (pref.getLong(Const.POEMS, 0) /
                    DateUnit.SEC_IN_MILLS / DateUnit.DAY_IN_SEC).toInt()
            epistlesDays = (pref.getLong(Const.EPISTLES, 0) /
                    DateUnit.SEC_IN_MILLS / DateUnit.DAY_IN_SEC).toInt()
        }
        if (poemsDays < DateHelper.MIN_DAYS_POEMS)
            poemsDays = DateHelper.MIN_DAYS_POEMS
        if (DateHelper.isLoadedOtkr()) {
            if (epistlesDays < DateHelper.MIN_DAYS_OLD_BOOK)
                epistlesDays = DateHelper.MIN_DAYS_OLD_BOOK
        } else if (epistlesDays < DateHelper.MIN_DAYS_NEW_BOOK)
            epistlesDays = DateHelper.MIN_DAYS_NEW_BOOK
    }
}