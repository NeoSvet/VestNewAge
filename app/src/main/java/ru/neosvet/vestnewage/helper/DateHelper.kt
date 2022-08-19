package ru.neosvet.vestnewage.helper

import android.content.Context
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.utils.Const

object DateHelper {
    private var loadedOtkr: Boolean? = null
    const val MIN_DAYS_POEMS = 16832 //Февраль 2016
    const val MIN_DAYS_OLD_BOOK = 12631 //Август 2004
    const val MIN_DAYS_NEW_BOOK = 16801 //Январь 2016
    const val MAX_DAYS_BOOK = 17045 //Сентябрь 2016

    fun isLoadedOtkr(): Boolean {
        if (loadedOtkr == null) {
            val pref = App.context.getSharedPreferences(BookHelper.TAG, Context.MODE_PRIVATE)
            loadedOtkr = pref.getBoolean(Const.OTKR, false)
        }
        return loadedOtkr!!
    }

    fun setLoadedOtkr(value: Boolean) {
        loadedOtkr = value
        val pref = App.context.getSharedPreferences(BookHelper.TAG, Context.MODE_PRIVATE)
        val editor = pref.edit()
        editor.putBoolean(Const.OTKR, value)
        editor.apply()
    }
}