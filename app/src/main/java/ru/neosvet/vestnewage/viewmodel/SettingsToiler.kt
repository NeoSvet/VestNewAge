package ru.neosvet.vestnewage.viewmodel

import androidx.work.Data
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import java.io.File

class SettingsToiler : NeoToiler() {
    companion object {
        const val CLEAR_CACHE = 0
        const val CLEAR_MARKERS = 1
        const val CLEAR_ARTICLES = 2
        const val CLEAR_DOCTRINE = 3
        const val CLEAR_OLD_BOOK = 4
        const val CLEAR_NEW_BOOK = 5
        const val CLEAR_NOW_BOOK = 6
    }

    private var size: Long = 0
    val panels = mutableListOf(true, false, false, false, false, false)
    private var currentYear: Int = 0
        get() {
            if (field == 0) field = DateUnit.initToday().year
            return field
        }

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, "Settings.Clear")
        .build()

    fun startClear(request: List<Int>) {
        if (isRun) return
        isRun = true
        scope.launch {
            size = 0
            request.forEach {
                when (it) {
                    CLEAR_CACHE ->
                        clearFolder(Lib.getFileP("/cache"))
                    CLEAR_MARKERS ->
                        deleteFile(DataBase.MARKERS)
                    CLEAR_ARTICLES ->
                        deleteFile(DataBase.ARTICLES)
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
            postState(NeoState.LongValue(size))
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
}