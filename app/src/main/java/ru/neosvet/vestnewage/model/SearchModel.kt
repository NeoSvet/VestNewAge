package ru.neosvet.vestnewage.model

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.work.Data
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.neosvet.utils.Const
import ru.neosvet.utils.DataBase
import ru.neosvet.utils.Lib
import ru.neosvet.utils.percent
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.helpers.DateHelper
import ru.neosvet.vestnewage.helpers.SearchHelper
import ru.neosvet.vestnewage.list.paging.FactoryEvents
import ru.neosvet.vestnewage.list.paging.SearchFactory
import ru.neosvet.vestnewage.model.basic.*
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.storage.SearchStorage
import java.util.*

class SearchModel : NeoViewModel(), FactoryEvents {
    companion object {
        private const val MODE_POSLANIYA = 0
        private const val MODE_KATRENY = 1
        private const val MODE_TITLES = 2
        private const val MODE_ALL = 3
        private const val MODE_LINKS = 4
        private const val DELAY_UPDATE = 1500
        const val MODE_BOOK = 5
        const val MODE_RESULTS = 6
    }

    private val factory: SearchFactory by lazy {
        SearchFactory(storage, this)
    }
    private var isInit = false
    private lateinit var strings: SearchStrings
    private val storage = SearchStorage()
    private val pages = PageStorage()
    private var countMatches: Int = 0
    private var labelMode = ""
    lateinit var helper: SearchHelper
        private set
    var loading = false
        private set
    var shownResult = false
        private set
    private val locale = Locale.forLanguageTag("ru")

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

    fun paging() = Pager(
        config = PagingConfig(
            pageSize = Const.MAX_ON_PAGE,
            prefetchDistance = 3
        ),
        pagingSourceFactory = { factory }
    ).flow

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
            labelMode = if (mode == MODE_RESULTS)
                strings.search_in_results
            else
                strings.search_mode[mode]
            countMatches = 0
            helper.countMaterials = 0
            SearchFactory.offset = 0
            notifyResult()
            shownResult = true
            storage.open()
            if (mode == MODE_RESULTS)
                searchInResults(helper.isDesc)
            else
                searchInPages(mode)
            isRun = false
            notifyResult()
        }
    }

    private fun searchInPages(mode: Int) = helper.run {
        storage.clear()
        storage.isDesc = isDesc
        if (mode == MODE_ALL)
            searchList(DataBase.ARTICLES, mode)
        val d = DateHelper.putYearMonth(start.year, start.month)
        val step = if (isDesc) -1 else 1
        var prev = 0
        var time: Long = 0
        while (isRun) {
            publishProgress(d)
            searchList(d.my, mode)
            if (d.timeInDays == end.timeInDays) break
            d.changeMonth(step)
            val now = System.currentTimeMillis()
            if (countMaterials - prev > Const.MAX_ON_PAGE &&
                now - time > DELAY_UPDATE
            ) {
                time = now
                notifyResult()
                prev = countMaterials
            }
        }
        pages.close()
    }

    private fun notifyResult() {
        helper.run {
            label = String.format(
                strings.format_found,
                labelMode.substring(labelMode.indexOf(" ") + 1),
                request,
                countMatches,
                countMaterials
            )
        }
        factory.total = helper.countMaterials
        mstate.postValue(Success)
    }

    fun showLastResult() {
        shownResult = true
        scope.launch {
            if (helper.countMaterials == 0) {
                val cursor = storage.getResults(helper.isDesc)
                helper.countMaterials = if (cursor.moveToFirst())
                    cursor.count else 0
                cursor.close()
            }
            factory.total = helper.countMaterials
            mstate.postValue(Success)
        }
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

    private fun searchInResults(reverseOrder: Boolean) {
        val title: MutableList<String> = ArrayList()
        val link: MutableList<String> = ArrayList()
        val id: MutableList<String> = ArrayList()
        var cursor = storage.getResults(reverseOrder)
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
        var prev = 0
        var time: Long = 0
        for (i in title.indices) {
            pages.open(link[i])
            cursor = pages.searchParagraphs(link[i], helper.request)
            if (cursor.moveToFirst()) {
                val row = ContentValues()
                row.put(Const.TITLE, title[i])
                row.put(Const.LINK, link[i])
                des = StringBuilder(getDes(cursor.getString(0), helper.request))
                helper.countMaterials++
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
            } else {
                val now = System.currentTimeMillis()
                if (helper.countMaterials - prev > Const.MAX_ON_PAGE &&
                    now - time > DELAY_UPDATE
                ) {
                    time = now
                    notifyResult()
                    prev = helper.countMaterials
                }
            }
        }
        pages.close()
        title.clear()
        link.clear()
        id.clear()
    }

    private fun getDes(des: String, sel: String): String {
        var d = Lib.withOutTags(des)
        val b = StringBuilder(d)
        d = d.lowercase(locale)
        val s = sel.lowercase(locale)
        var i = -1
        var x = 0
        while (d.indexOf(s, i + 1).also { i = it } > -1) {
            b.insert(i + x + s.length, "</b></font>")
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
        storage.open()
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
                        helper.countMaterials++
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

    override fun startLoad() {
        loading = true
    }

    override fun finishLoad() {
        if (isRun) return
        viewModelScope.launch {
            delay(300)
            mstate.postValue(Ready)
            loading = false
        }
    }
}