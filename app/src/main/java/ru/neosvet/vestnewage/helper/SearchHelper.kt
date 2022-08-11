package ru.neosvet.vestnewage.helper

import android.content.Context
import android.content.SharedPreferences
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.SearchEngine
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileReader
import java.io.FileWriter

class SearchHelper(context: Context) {
    companion object {
        const val TAG = "Search"
        const val REQUESTS_LIMIT = 20
        const val LABEL = "l"
        const val INVERT = "inv"
        const val LETTER_CASE = "reg"
        const val PREFIX = "pri"
        const val ENDING = "okon"
        const val ALL_WORDS = "all"
        const val BY_WORDS = "words"
        const val I_INVERT = 0
        const val I_LETTER_CASE = 1
        const val I_BY_WORDS = 2
        const val I_PREFIX = 3
        const val I_ENDING = 4
        const val I_ALL_WORDS = 5
    }

    enum class Type {
        NORMAL, LOAD_MONTH, LOAD_PAGE
    }

    private val pref = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
    private val editor: SharedPreferences.Editor = pref.edit()
    var isNeedLoad = false
    var start: DateUnit
    var end: DateUnit
    var mode: Int = 0
    var countMaterials: Int = 0
    val minMonth: Int
    val minYear: Int
    var label: String = ""
    var request: String = ""
    val options = mutableListOf<Boolean>()
    val requests = mutableListOf<String>()
    private val optionsNames = mutableListOf<String>()
    private val stringOptionsOff = context.resources.getString(R.string.all_turn_off)
    private val stringRange = context.resources.getString(R.string.range)
    var optionsString = ""
        private set
    val isDesc: Boolean
        get() = options[I_INVERT]
    val isLetterCase: Boolean
        get() = options[I_LETTER_CASE]
    val isByWords: Boolean
        get() = options[I_BY_WORDS]
    val isPrefix: Boolean
        get() = options[I_PREFIX]
    val isEnding: Boolean
        get() = options[I_ENDING]
    val isAllWords: Boolean
        get() = options[I_ALL_WORDS]

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

        mode = pref.getInt(Const.MODE, SearchEngine.MODE_BOOK)
        options.add(pref.getBoolean(INVERT, false))
        options.add(pref.getBoolean(LETTER_CASE, false))
        options.add(pref.getBoolean(BY_WORDS, false))
        options.add(pref.getBoolean(PREFIX, true))
        options.add(pref.getBoolean(ENDING, true))
        options.add(pref.getBoolean(ALL_WORDS, true))

        context.resources.getStringArray(R.array.search_options).forEach {
            optionsNames.add(it.lowercase())
        }
        initOptionsString()
    }

    fun getListRequests(): List<String> {
        val f = Lib.getFileS(Const.SEARCH)
        if (f.exists() && requests.isEmpty()) {
            val br = BufferedReader(FileReader(f))
            var s: String? = br.readLine()
            while (s != null) {
                requests.add(s)
                s = br.readLine()
            }
            br.close()
        }
        return requests
    }

    fun saveRequest() {
        val f = Lib.getFileS(Const.SEARCH)
        f.delete()
        val bw = BufferedWriter(FileWriter(f))
        requests.forEach {
            bw.write(it + Const.N)
        }
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
        this.mode = mode
        editor.putInt(Const.MODE, mode)
        editor.putInt(Const.START, start.timeInDays)
        editor.putInt(Const.END, end.timeInDays)
        val names = listOf(INVERT, LETTER_CASE, BY_WORDS, PREFIX, ENDING, ALL_WORDS)
        for (i in names.indices)
            editor.putBoolean(names[i], options[i])
        editor.apply()
        initOptionsString()
    }

    private fun initOptionsString() {
        val sb = StringBuilder()
        for (i in options.indices) {
            if (i == I_LETTER_CASE && mode == SearchEngine.MODE_LINKS)
                break
            if (options[i]) {
                sb.append(", ")
                sb.append(optionsNames[i])
            } else if (i == I_BY_WORDS)
                break
        }
        if (sb.isEmpty()) {
            optionsString = stringOptionsOff
        } else {
            sb.delete(0, 2)
            sb.append(".")
            optionsString = sb.toString()
        }
        if (mode != SearchEngine.MODE_DOCTRINE)
            optionsString += " $stringRange ${start.my}-${end.my}."
    }

    fun deleteBase() {
        val f = Lib.getFileDB(Const.SEARCH)
        if (f.exists()) f.delete()
    }

    fun clearRequests() {
        requests.clear()
        val f = Lib.getFileS(Const.SEARCH)
        if (f.exists()) f.delete()
    }

    fun getType(item: ListItem): Type {
        if (item.link.length == 5) return Type.LOAD_MONTH
        if (item.title.contains(Const.HTML)) return Type.LOAD_PAGE
        return Type.NORMAL
    }
}
