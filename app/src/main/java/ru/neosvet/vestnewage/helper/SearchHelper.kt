package ru.neosvet.vestnewage.helper

import android.content.Context
import android.content.SharedPreferences
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.model.SearchModel
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileReader
import java.io.FileWriter

class SearchHelper(context: Context) {
    companion object {
        const val TAG = "Search"
        const val LABEL = "l"
    }

    enum class Type {
        NORMAL, LOAD_MONTH, LOAD_PAGE
    }

    private val pref = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
    private val editor: SharedPreferences.Editor = pref.edit()
    var start: DateUnit
    var end: DateUnit
    var countMaterials: Int = 0
    val minMonth: Int
    val minYear: Int
    var label: String = ""
    var request: String = ""
    val isDesc: Boolean
        get() = start.timeInMills > end.timeInMills

    init {
        val f = Lib.getFileDB("12.15")
        if (f.exists()) {
            // если последний загружаемый месяц с сайта Откровений загружен, значит расширяем диапозон поиска
            minMonth = 8 //aug
            minYear = 2004
        } else {
            minMonth = 1
            minYear = 2016
        }

        val d = pref.getInt(Const.START, 0)
        if (d == 0) {
            end = DateUnit.initToday()
            //if (mode < 5)// открываем ссылку с сайта Благая Весть
            //    start = DateHelper.putYearMonth(act, 2016, 1);
            start = DateUnit.putYearMonth(minYear, minMonth)
        } else {
            start = DateUnit.putDays(d)
            end = DateUnit.putDays(pref.getInt(Const.END, 0))
        }
        start.day = 1
        end.day = 1
    }

    fun existsResults() = Lib.getFileDB(Const.SEARCH).exists()

    fun getListRequests(): List<String> {
        val f = Lib.getFileS(Const.SEARCH)
        val list = mutableListOf<String>()
        if (f.exists()) {
            val br = BufferedReader(FileReader(f))
            var s: String? = br.readLine()
            while (s != null) {
                list.add(s)
                s = br.readLine()
            }
            br.close()
        }
        return list
    }

    fun changeDates() {
        val d = start
        start = end
        end = d
    }

    fun saveRequest(request: String) {
        val f = Lib.getFileS(Const.SEARCH)
        val bw = BufferedWriter(FileWriter(f, true))
        bw.write(request + Const.N)
        bw.close()
    }

    fun loadLastResult() {
        label = pref.getString(LABEL, "")!!
        if (label.contains("“"))
            request = label.substring(label.indexOf("“") + 1, label.indexOf(Const.N) - 2)
    }

    fun saveLastResult() {
        if (label.isNotEmpty())
            editor.putString(LABEL, label).apply()
    }

    fun savePerformance(mode: Int) {
        editor.putInt(Const.MODE, mode)
        editor.putInt(Const.START, start.timeInDays)
        editor.putInt(Const.END, end.timeInDays)
        editor.apply()
    }

    fun deleteBase() {
        val f = Lib.getFileDB(Const.SEARCH)
        if (f.exists()) f.delete()
    }

    fun clearRequests() {
        val f = Lib.getFileS(Const.SEARCH)
        if (f.exists()) f.delete()
    }

    fun loadMode(): Int =
        pref.getInt(Const.MODE, SearchModel.MODE_BOOK)

    fun getType(item: ListItem): Type {
        if (item.link.length == 5) return Type.LOAD_MONTH
        if (item.title.contains("/")) return Type.LOAD_PAGE
        return Type.NORMAL
    }
}
