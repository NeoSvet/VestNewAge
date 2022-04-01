package ru.neosvet.vestnewage.loader

import ru.neosvet.vestnewage.helpers.DateHelper
import ru.neosvet.vestnewage.helpers.LoaderHelper
import ru.neosvet.vestnewage.storage.PageStorage
import java.io.BufferedWriter
import java.io.FileWriter

class CalendarLoader(
    private var year: Int,
    private var month: Int
) : ListLoader {
    constructor() : this(0, 0)

    fun setDate(year: Int, month: Int) {
        this.year = year
        this.month = month
    }

    override fun getLinkList(): Int {
        val d = DateHelper.putYearMonth(year, month)
        val storage = PageStorage()
        storage.open(d.my)
        val curTitle = storage.getLinks()
        var k = 0
        if (curTitle.moveToFirst()) {
            // пропускаем первую запись - там только дата изменения списка
            var link: String?
            val file = LoaderHelper.getFileList()
            val bw = BufferedWriter(FileWriter(file))
            while (curTitle.moveToNext()) {
                link = curTitle.getString(0)
                bw.write(link)
                k++
                bw.newLine()
                bw.flush()
            }
            bw.close()
        }
        curTitle.close()
        storage.close()
        return k
    }
}