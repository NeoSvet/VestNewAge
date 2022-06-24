package ru.neosvet.vestnewage.viewmodel

import androidx.work.Data
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.helper.BookHelper
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import java.io.File

class SettingsToiler : NeoToiler() {
    private var size: Long = 0
    val panels = mutableListOf(true, false, false, false, false, false)

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, "Settings.Clear")
        .build()

    fun startClear(request: List<String>) {
        if (isRun) return
        isRun = true
        scope.launch {
            size = 0
            var f: File
            for (r in request) {
                if (r == Const.START || r == Const.END) { //book
                    var d: DateUnit
                    var maxY: Int
                    var maxM: Int
                    if (r == Const.START) { //book prev years
                        d = DateUnit.initToday()
                        maxY = d.year - 1
                        maxM = 12
                        d = DateUnit.putYearMonth(2004, 8)
                        val book = BookHelper()
                        book.setLoadedOtkr(false)
                    } else { //book cur year
                        d = DateUnit.initToday()
                        maxY = d.year
                        maxM = d.month
                        d = DateUnit.putYearMonth(maxY, 1)
                    }
                    while (d.year < maxY || d.year == maxY && d.month <= maxM) {
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
                } else if (r == Const.FILE) { //cache
                    clearFolder(Lib.getFileP("/cache"))
                } else { //markers or materials
                    f = Lib.getFileDB(r)
                    if (f.exists()) {
                        size += f.length()
                        f.delete()
                    }
                }
            }
            postState(NeoState.LongValue(size))
            isRun = false
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