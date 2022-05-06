package ru.neosvet.vestnewage.model

import androidx.work.Data
import kotlinx.coroutines.launch
import ru.neosvet.utils.Const
import ru.neosvet.utils.Lib
import ru.neosvet.vestnewage.helpers.BookHelper
import ru.neosvet.vestnewage.helpers.DateHelper
import ru.neosvet.vestnewage.model.basic.LongState
import ru.neosvet.vestnewage.model.basic.NeoViewModel
import java.io.File

class SettingsModel : NeoViewModel() {
    private var size: Long = 0
    val panels = booleanArrayOf(true, false, false, false, false)

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, "Settings.Clear")
        .build()

    override suspend fun doLoad() {
    }

    override fun onDestroy() {
    }

    fun startClear(request: List<String>) {
        scope.launch {
            size = 0
            var f: File
            for (r in request) {
                if (r == Const.START || r == Const.END) { //book
                    var d: DateHelper
                    var maxY: Int
                    var maxM: Int
                    if (r == Const.START) { //book prev years
                        d = DateHelper.initToday()
                        maxY = d.year - 1
                        maxM = 12
                        d = DateHelper.putYearMonth(2004, 8)
                        val book = BookHelper()
                        book.setLoadedOtkr(false)
                    } else { //book cur year
                        d = DateHelper.initToday()
                        maxY = d.year
                        maxM = d.month
                        d = DateHelper.putYearMonth(maxY, 1)
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
            mstate.postValue(LongState(size))
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