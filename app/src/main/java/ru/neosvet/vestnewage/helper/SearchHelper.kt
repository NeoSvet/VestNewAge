package ru.neosvet.vestnewage.helper

import android.content.Context
import android.content.SharedPreferences
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.SearchEngine

class SearchHelper(context: Context) {
    companion object {
        const val TAG = "Search"
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

    private val pref = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
    private val editor: SharedPreferences.Editor = pref.edit()
    var isNeedLoad = false
    var start: DateUnit
    var end: DateUnit
    var mode = 0
    var label = ""
    var request = ""
    private val _options = mutableListOf<Boolean>()
    val options: List<Boolean>
        get() = _options
    private val optionsNames = mutableListOf<String>()
    private val stringOptionsOff = context.resources.getString(R.string.all_turn_off)
    private val stringRange = context.resources.getString(R.string.range)
    var optionsString = ""
        private set
    val isDesc: Boolean
        get() = _options[I_INVERT]
    val isLetterCase: Boolean
        get() = _options[I_LETTER_CASE]
    val isByWords: Boolean
        get() = _options[I_BY_WORDS]
    val isPrefix: Boolean
        get() = _options[I_PREFIX]
    val isEnding: Boolean
        get() = _options[I_ENDING]
    val isAllWords: Boolean
        get() = _options[I_ALL_WORDS]

    init {
        val d = pref.getInt(Const.START, 0)
        if (d == 0) {
            end = DateUnit.initToday()
            start = if (DateHelper.isLoadedOtkr())
                DateUnit.putDays(DateHelper.MIN_DAYS_OLD_BOOK)
            else
                DateUnit.putDays(DateHelper.MIN_DAYS_NEW_BOOK)
        } else {
            start = DateUnit.putDays(d)
            end = DateUnit.putDays(pref.getInt(Const.END, 0))
        }
        start.day = 1
        end.day = 1

        mode = pref.getInt(Const.MODE, SearchEngine.MODE_BOOK)
        _options.add(pref.getBoolean(INVERT, false))
        _options.add(pref.getBoolean(LETTER_CASE, false))
        _options.add(pref.getBoolean(BY_WORDS, false))
        _options.add(pref.getBoolean(PREFIX, true))
        _options.add(pref.getBoolean(ENDING, true))
        _options.add(pref.getBoolean(ALL_WORDS, true))

        context.resources.getStringArray(R.array.search_options).forEach {
            optionsNames.add(it.trim().lowercase())
        }
        initOptionsString()
    }



    fun loadLastResult() { //Toiler
        label = pref.getString(LABEL, "")!!
        if (label.contains("“"))
            request = label.substring(label.indexOf("“") + 1, label.indexOf(Const.N) - 2)
    }

    fun saveLastResult() { //Toiler
        if (label.isNotEmpty())
            editor.putString(LABEL, label).apply()
    }

    fun savePerformance(mode: Int) { //Dialog
        this.mode = mode
        editor.putInt(Const.MODE, mode)
        editor.putInt(Const.START, start.timeInDays)
        editor.putInt(Const.END, end.timeInDays)
        val names = listOf(INVERT, LETTER_CASE, BY_WORDS, PREFIX, ENDING, ALL_WORDS)
        for (i in names.indices)
            editor.putBoolean(names[i], _options[i])
        editor.apply()
        initOptionsString()
    }

    private fun initOptionsString() {
        val sb = StringBuilder()
        for (i in _options.indices) {
            if (i == I_LETTER_CASE && mode == SearchEngine.MODE_LINKS)
                break
            if (_options[i]) {
                sb.append(", ")
                sb.append(optionsNames[i])
            } else if (i == I_BY_WORDS)
                break
        }
        optionsString = if (sb.isEmpty())
            stringOptionsOff
        else {
            sb.delete(0, 2)
            sb.append(".")
            sb.toString()
        }
        if (mode < SearchEngine.MODE_DOCTRINE)
            optionsString += " $stringRange ${start.my}-${end.my}."
    }

    fun putOptions(list: List<Boolean>) {
        _options.clear()
        _options.addAll(list)
    }
}
