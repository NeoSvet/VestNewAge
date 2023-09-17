package ru.neosvet.vestnewage.viewmodel

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.work.Data
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import java.io.File
import java.util.*

class SettingsToiler : NeoToiler() {
    companion object {
        const val CLEAR_CACHE = 0
        const val CLEAR_MARKERS = 1
        const val CLEAR_ART_AND_ADD = 2
        const val CLEAR_DOCTRINE = 3
        const val CLEAR_OLD_BOOK = 4
        const val CLEAR_NEW_BOOK = 5
        const val CLEAR_NOW_BOOK = 6
    }

    private var size: Long = 0
    private var task = ""
    private var currentYear: Int = 0
        get() {
            if (field == 0) field = DateUnit.initToday().year
            return field
        }

    private val alarmDays: ArrayList<Int> by lazy {
        arrayListOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY,
            Calendar.SUNDAY
        )
    }

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, "Settings.$task")
        .build()

    override fun init(context: Context) {
    }

    override suspend fun defaultState() {
    }

    fun startClear(request: List<Int>) {
        task = "Clear"
        isRun = true
        scope.launch {
            size = 0
            request.forEach {
                when (it) {
                    CLEAR_CACHE ->
                        clearFolder(Lib.getFileP("/cache"))
                    CLEAR_MARKERS ->
                        deleteFile(DataBase.MARKERS)
                    CLEAR_ART_AND_ADD -> {
                        deleteFile(DataBase.ARTICLES)
                        deleteFile(DataBase.ADDITION)
                    }
                    CLEAR_DOCTRINE ->
                        deleteFile(DataBase.DOCTRINE)
                    CLEAR_OLD_BOOK ->
                        clearBook(2004, 2015)
                    CLEAR_NEW_BOOK ->
                        clearBook(2016, currentYear - 2)
                    CLEAR_NOW_BOOK ->
                        clearBook(currentYear - 1, currentYear)
                }
            }
            postState(BasicState.Message(String.format("%.2f", size / 1048576f))) //to MegaByte
            isRun = false
        }
    }

    private fun clearBook(startYear: Int, endYear: Int) {
        val d = DateUnit.putYearMonth(startYear, 1)
        val max = DateUnit.putYearMonth(endYear + 1, 1).timeInDays
        var f: File
        while (d.timeInDays < max) {
            f = Lib.getFileDB(d.my)
            if (f.exists()) {
                size += f.length()
                f.delete()
                f = Lib.getFileDB(d.my + "-journal")
                if (f.exists()) {
                    size += f.length()
                    f.delete()
                }
            }
            d.changeMonth(1)
        }
    }

    private fun deleteFile(name: String) {
        val f = Lib.getFileDB(name)
        if (f.exists()) {
            size += f.length()
            f.delete()
        }
    }

    private fun clearFolder(folder: File) {
        folder.listFiles()?.forEach { f ->
            if (f.isFile)
                size += f.length()
            else
                clearFolder(f)
            f.delete()
        }
    }

    fun setAlarm(h: Int, v: Int) {
        task = "OnAlarm"
        val d = DateUnit.initNow()
        d.setSeconds(0)
        d.setMinutes(0)
        d.setHours(h)
        d.changeSeconds(d.offset - DateUnit.OFFSET_MSK)
        d.changeMinutes(-(v + 1))

        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
        // intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        intent.putExtra(AlarmClock.EXTRA_HOUR, d.hour)
        intent.putExtra(AlarmClock.EXTRA_MINUTES, d.minute)
        intent.putExtra(AlarmClock.EXTRA_ALARM_SNOOZE_DURATION, 1)
        intent.putExtra(AlarmClock.EXTRA_DAYS, alarmDays)
        intent.putExtra(
            AlarmClock.EXTRA_MESSAGE,
            App.context.getString(R.string.prom_for_soul_unite)
        )
        App.context.startActivity(intent)
    }

    fun offAlarm() {
        task = "OffAlarm"
        val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM)
        intent.putExtra(
            AlarmClock.ALARM_SEARCH_MODE_LABEL,
            App.context.getString(R.string.prom_for_soul_unite)
        )
        App.context.startActivity(intent)
    }
}