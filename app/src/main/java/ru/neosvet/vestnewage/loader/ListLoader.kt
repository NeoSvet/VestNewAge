package ru.neosvet.vestnewage.loader

import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.loader.basic.LoadHandler
import ru.neosvet.vestnewage.loader.basic.Loader
import ru.neosvet.vestnewage.loader.page.PageLoader
import ru.neosvet.vestnewage.model.SiteModel
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Lib

class ListLoader(private val handler: LoadHandler) : Loader {
    enum class Type {
        ALL, SITE, BOOK
    }

    private var loader: PageLoader =
        PageLoader()
    private var isRun = false

    override fun cancel() {
        isRun = false
    }

    private fun loadList(links: List<String>) {
        for (link in links) {
            loader.download(link, false)
            handler.upProg()
            if (isRun.not()) return
        }
        loader.finish()
    }

    fun loadYear(year: Int) {
        isRun = true
        val loader = CalendarLoader()
        var d = DateUnit.initToday()
        val m = if (d.year != year) 13
        else d.month + 1
        handler.postMessage(App.context.getString(R.string.download_list))
        loader.loadListYear(year, m)
        var i = 1
        var k = 0
        while (i < m && isRun) {
            d = DateUnit.putYearMonth(year, i)
            k += countBookList(d.my)
            i++
        }
        handler.setMax(k)
        i = 1
        while (i < m && isRun) {
            loader.setDate(year, i)
            setLoadMsg(loader.curDate)
            loadList(loader.getLinkList())
            i++
        }
    }

    fun loadSection(type: Type) {
        isRun = true
        loader = PageLoader()
        // подсчёт количества страниц:
        var k = 0
        if (type == Type.ALL || type == Type.BOOK)
            k = workWithBook(true)
        if (type == Type.ALL || type == Type.SITE) {
            val loader = SiteLoader(Lib.getFile(SiteModel.MAIN).toString())
            val list = loader.getLinkList()
            k += list.size
            handler.setMax(k)
            handler.postMessage(App.context.getString(R.string.materials))
            loadList(list) // загрузка статей
        } else
            handler.setMax(k)
        // загрузка книги:
        if (isRun.not()) return
        if (type == Type.ALL || type == Type.BOOK) {
            workWithBook(false)
        }
    }

    private fun workWithBook(calc: Boolean): Int {
        val end_year: Int
        val end_month: Int
        var k = 0
        val d = DateUnit.initToday()
        d.day = 1
        end_month = d.month
        end_year = d.year
        d.month = 1
        d.year = end_year - 1
        while (isRun) {
            if (calc)
                k += countBookList(d.my)
            else {
                setLoadMsg(d)
                loadBookList(d.my)
            }
            if (d.year == end_year && d.month == end_month) break
            d.changeMonth(1)
        }
        return k
    }

    private fun setLoadMsg(date: DateUnit) {
        handler.postMessage(date.monthString + " " + date.year)
    }

    private fun countBookList(name: String): Int {
        val storage = PageStorage()
        storage.open(name)
        val curTitle = storage.getLinks()
        val k = curTitle.count - 1
        curTitle.close()
        storage.close()
        return k
    }

    private fun loadBookList(name: String) {
        val storage = PageStorage()
        storage.open(name)
        val curTitle = storage.getLinks()
        if (curTitle.moveToFirst()) {
            // пропускаем первую запись - там только дата изменения списка
            while (curTitle.moveToNext()) {
                loader.download(curTitle.getString(0), false)
                handler.upProg()
            }
        }
        loader.finish()
        curTitle.close()
        storage.close()
    }
}