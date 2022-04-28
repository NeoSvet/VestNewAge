package ru.neosvet.vestnewage.model

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import androidx.work.Data
import kotlinx.coroutines.launch
import ru.neosvet.utils.Const
import ru.neosvet.utils.DataBase
import ru.neosvet.utils.Lib
import ru.neosvet.utils.percent
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.helpers.DateHelper
import ru.neosvet.vestnewage.helpers.ProgressHelper
import ru.neosvet.vestnewage.helpers.SearchHelper
import ru.neosvet.vestnewage.list.ListItem
import ru.neosvet.vestnewage.model.basic.MessageState
import ru.neosvet.vestnewage.model.basic.NeoViewModel
import ru.neosvet.vestnewage.model.basic.SearchStrings
import ru.neosvet.vestnewage.model.basic.SuccessList
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.storage.SearchStorage

class SearchModel : NeoViewModel() {
    companion object {
        private const val MODE_POSLANIYA = 0
        private const val MODE_KATRENY = 1
        private const val MODE_TITLES = 2
        private const val MODE_ALL = 3
        private const val MODE_LINKS = 4
        const val MODE_BOOK = 5
        const val MODE_RESULTS = 6
    }

    private var isInit = false
    private lateinit var strings: SearchStrings
    private val storage = SearchStorage()
    private val pages = PageStorage()
    private var countMatches: Int = 0
    lateinit var helper: SearchHelper
        private set

    fun init(context: Context) {
        if (isInit) return
        helper = SearchHelper(context)
        strings = SearchStrings(
            format_search_date = context.getString(R.string.format_search_date),
            format_search_proc = context.getString(R.string.format_search_proc),
            search_in_results = context.getString(R.string.search_in_results),
            search_mode = context.resources.getStringArray(R.array.search_mode),
            format_found = context.getString(R.string.format_found),
        )
        isInit = true
    }

    override suspend fun doLoad() {
    }

    override fun onDestroy() {
        pages.close()
        storage.close()
    }

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, Const.SEARCH)
        .putString(Const.STRING, helper.request)
        .putString(Const.START, helper.start.my)
        .putString(Const.END, helper.end.my)
        .build()

    fun startSearch(request: String, mode: Int) {
        helper.request = request
        scope.launch {
            isRun = true
            helper.run {
                storage.reopen()
                val step = if (start.timeInDays > end.timeInDays) -1 else 1
                if (mode == MODE_RESULTS) {
                    searchInResults(step == -1)
                    return@run
                }
                storage.clear()
                if (mode == MODE_ALL)
                    searchList(DataBase.ARTICLES, mode)
                val d = DateHelper.putYearMonth(start.year, start.month)
                while (isRun) {
                    publishProgress(d)
                    searchList(d.my, mode)
                    if (d.timeInDays == end.timeInDays) break
                    d.changeMonth(step)
                }
                pages.close()
            }
            val s = if (mode == MODE_RESULTS)
                strings.search_in_results
            else
                strings.search_mode[mode]
            initLabel(s)
            showResult(0)
        }
    }

    private fun initLabel(string: String) = helper.run {
        label = String.format(
            strings.format_found,
            string.substring(string.indexOf(" ") + 1),
            request,
            countMatches,
            countPages
        )
    }

    private fun publishProgress(d: DateHelper) {
        mstate.postValue(
            MessageState(
                String.format(
                    strings.format_search_date,
                    d.monthString, d.year
                )
            )
        )
    }

    fun showResult(page: Int) {
        helper.page = page
        val result = arrayListOf<ListItem>()
        val desc = helper.start.timeInMills > helper.end.timeInMills
        storage.reopen()
        val cursor = storage.getResults(desc)
        if (cursor.count == 0) {
            helper.countPages = 0
            mstate.postValue(SuccessList(result))
            cursor.close()
            storage.close()
            helper.deleteBase()
            return
        }
        val position = page * Const.MAX_ON_PAGE
        if (position < 0 || !cursor.moveToPosition(position)) return

        var max = cursor.count / Const.MAX_ON_PAGE
        if (cursor.count % Const.MAX_ON_PAGE > 0) max++
        val iTitle = cursor.getColumnIndex(Const.TITLE)
        val iLink = cursor.getColumnIndex(Const.LINK)
        val iDes = cursor.getColumnIndex(Const.DESCTRIPTION)
        do {
            val item = ListItem(cursor.getString(iTitle), cursor.getString(iLink))
            cursor.getString(iDes)?.let {
                item.des = it
            }
            result.add(item)
        } while (cursor.moveToNext() && result.size < Const.MAX_ON_PAGE)
        cursor.close()
        helper.countPages = max
        mstate.postValue(SuccessList(result))
    }

    private fun searchInResults(reverseOrder: Boolean) {
        val title: MutableList<String> = ArrayList()
        val link: MutableList<String> = ArrayList()
        val id: MutableList<String> = ArrayList()
        val cursor = storage.getResults(reverseOrder)
        if (cursor.moveToFirst()) {
            val iTitle = cursor.getColumnIndex(Const.TITLE)
            val iLink = cursor.getColumnIndex(Const.LINK)
            val iID = cursor.getColumnIndex(DataBase.ID)
            do {
                title.add(cursor.getString(iTitle))
                link.add(cursor.getString(iLink))
                id.add(cursor.getInt(iID).toString())
            } while (cursor.moveToNext())
        }
        cursor.close()
        var des: StringBuilder
        var p1 = -1
        var p2: Int
        countMatches = 0
        helper.countPages = 0
        for (i in title.indices) {
            pages.open(link[i])
            val cursor = pages.searchParagraphs(link[i], helper.request)
            if (cursor.moveToFirst()) {
                val row = ContentValues()
                row.put(Const.TITLE, title[i])
                row.put(Const.LINK, link[i])
                des = StringBuilder(getDes(cursor.getString(0), helper.request))
                helper.countPages++
                while (cursor.moveToNext()) {
                    des.append(Const.BR + Const.BR)
                    des.append(getDes(cursor.getString(0), helper.request))
                }
                row.put(Const.DESCTRIPTION, des.toString())
                storage.update(id[i], row)
            } else {
                storage.delete(id[i])
            }
            cursor.close()
            p2 = i.percent(title.size)
            if (p1 < p2) {
                p1 = p2
                mstate.postValue(MessageState(String.format(strings.format_search_proc, p1)))
            }
        }
        pages.close()
        title.clear()
        link.clear()
        id.clear()
    }

    private fun getDes(d: String, sel: String): String {
        var d = Lib.withOutTags(d)
        val b = StringBuilder(d)
        d = d.toLowerCase()
        val sel = sel.toLowerCase()
        var i = -1
        var x = 0
        while (d.indexOf(sel, i + 1).also { i = it } > -1) {
            b.insert(i + x + sel.length, "</b></font>")
            b.insert(i + x, "<font color='#99ccff'><b>")
            x += 36
            countMatches++
        }
        return b.toString().replace(Const.N, Const.BR)
    }

    @SuppressLint("Range")
    private fun searchList(name: String, mode: Int) {
        pages.open(name)
        var n = name.substring(3).toInt() * 650 +
                name.substring(0, 2).toInt() * 50
        val cursor: Cursor = when (mode) {
            MODE_TITLES ->
                pages.searchTitle(helper.request)
            MODE_LINKS ->
                pages.searchLink(helper.request)
            else -> { //везде: 3 или 5 (по всем материалам или в Посланиях и Катренах)
                //фильтрация по 0 и 1 будет позже
                pages.searchParagraphs(helper.request)
            }
        }
        if (!cursor.moveToFirst()) {
            cursor.close()
            return
        }
        val iPar = cursor.getColumnIndex(DataBase.PARAGRAPH)
        val iID = cursor.getColumnIndex(DataBase.ID)
        var row: ContentValues? = null
        var id = -1
        var add = true
        val des = StringBuilder()
        storage.reopen()
        do {
            if (id == cursor.getInt(iID) && add) {
                des.append(Const.BR + Const.BR)
                des.append(getDes(cursor.getString(iPar), helper.request))
            } else {
                id = cursor.getInt(iID)
                val curTitle = pages.getPageById(id)
                if (curTitle.moveToFirst()) {
                    val link = curTitle.getString(curTitle.getColumnIndex(Const.LINK))
                    val iTitle = curTitle.getColumnIndex(Const.TITLE)
                    if (mode == MODE_POSLANIYA)
                        add = !link.contains(Const.POEMS)
                    else if (mode == MODE_KATRENY)
                        add = link.contains(Const.POEMS)
                    if (add) {
                        val title = pages.getPageTitle(curTitle.getString(iTitle), link)
                        if (row != null) {
                            if (des.isNotEmpty()) {
                                row.put(Const.DESCTRIPTION, des.toString())
                                des.clear()
                            }
                            storage.insert(row)
                        }
                        row = ContentValues()
                        row.put(Const.TITLE, title)
                        row.put(Const.LINK, link)
                        row.put(DataBase.ID, n)
                        n++
                        helper.countPages++
                        if (iPar > -1) //если нужно добавлять абзац (при поиске в заголовках и датах не надо)
                            des.append(getDes(cursor.getString(iPar), helper.request))
                    }
                }
                curTitle.close()
            }
        } while (cursor.moveToNext())
        if (row != null) {
            if (des.isNotEmpty())
                row.put(Const.DESCTRIPTION, des.toString())
            storage.insert(row)
        }
        cursor.close()
    }
}