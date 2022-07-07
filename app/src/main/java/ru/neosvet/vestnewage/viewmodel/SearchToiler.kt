package ru.neosvet.vestnewage.viewmodel

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import androidx.work.Data
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.helper.SearchHelper
import ru.neosvet.vestnewage.loader.CalendarLoader
import ru.neosvet.vestnewage.loader.page.PageLoader
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.storage.SearchStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.isPoem
import ru.neosvet.vestnewage.utils.percent
import ru.neosvet.vestnewage.view.list.paging.NeoPaging
import ru.neosvet.vestnewage.view.list.paging.SearchFactory
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.basic.SearchStrings
import java.util.*

class SearchToiler : NeoToiler(), NeoPaging.Parent {
    companion object {
        private const val MODE_EPISTLES = 0
        private const val MODE_POEMS = 1
        private const val MODE_TITLES = 2
        private const val MODE_ALL = 3
        private const val MODE_LINKS = 4
        private const val DELAY_UPDATE = 1500
        const val MODE_BOOK = 5
        const val MODE_RESULTS = 6
    }

    private val paging = NeoPaging(this)
    override val factory: SearchFactory by lazy {
        SearchFactory(storage, paging)
    }
    val isLoading: Boolean
        get() = paging.isPaging
    private var isInit = false
    private lateinit var strings: SearchStrings
    private val storage = SearchStorage()
    private val pages = PageStorage()
    private var countMatches: Int = 0
    private var labelMode = ""
    private var mode: Int = -1
    lateinit var helper: SearchHelper
        private set
    var shownResult = false
        private set
    private var loadDate: String? = null
    private var loadLink: String? = null
    private val locale = Locale.forLanguageTag("ru")

    fun init(context: Context) {
        if (isInit) return
        helper = SearchHelper(context)
        strings = SearchStrings(
            format_search_date = context.getString(R.string.format_search_date),
            format_search_proc = context.getString(R.string.format_search_proc),
            format_month_no_loaded = context.getString(R.string.format_month_no_loaded),
            format_page_no_loaded = context.getString(R.string.format_page_no_loaded),
            format_load = context.getString(R.string.format_load),
            not_found = context.getString(R.string.not_found),
            search_in_results = context.getString(R.string.search_in_results),
            search_mode = context.resources.getStringArray(R.array.search_mode),
            format_found = context.getString(R.string.format_found),
        )
        isInit = true
    }

    override suspend fun doLoad() {
        loadDate?.let { date ->
            val d = dateFromString(date)
            postState(
                NeoState.Message(
                    String.format(strings.format_load, d.monthString + " " + d.year)
                )
            )
            val loader = CalendarLoader()
            loader.setDate(d.year, d.month)
            loader.loadListMonth(false)
            val links = loader.getLinkList()
            val pageLoader = PageLoader()
            storage.deleteByLink(date)
            for (link in links) {
                pageLoader.download(link, false)
                if (isRun.not()) break
            }
            pageLoader.finish()
            searchList(date)
            notifyResult()
            loadDate = null
        }
        loadLink?.let { link ->
            postState(NeoState.Message(String.format(strings.format_load, link)))
            val id = storage.getIdByLink(link)
            storage.delete(id.toString())
            val pageLoader = PageLoader()
            pageLoader.download(link, true)
            pageLoader.finish()
            val item = findInPage(link, id)
            postState(NeoState.ListValue(listOf(item)))
            loadLink = null
        }
    }

    override fun onDestroy() {
        pages.close()
        storage.close()
    }

    override fun getInputData(): Data {
        loadDate?.let {
            return Data.Builder()
                .putString(Const.TASK, Const.SEARCH)
                .putString(Const.TIME, it)
                .build()
        }
        loadLink?.let {
            return Data.Builder()
                .putString(Const.TASK, Const.SEARCH)
                .putString(Const.LINK, it)
                .build()
        }
        return Data.Builder()
            .putString(Const.TASK, Const.SEARCH)
            .putString(Const.STRING, helper.request)
            .putString(Const.START, helper.start.my)
            .putString(Const.END, helper.end.my)
            .build()
    }

    fun startSearch(request: String, mode: Int) {
        this.mode = mode
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
                searchInPages()
            isRun = false
            notifyResult()
        }
    }

    private suspend fun searchInPages() = helper.run {
        storage.clear()
        storage.isDesc = isDesc
        if (mode == MODE_ALL)
            searchList(DataBase.ARTICLES)
        val d = DateUnit.putYearMonth(start.year, start.month)
        val step = if (isDesc) -1 else 1
        var prev = 0
        var time: Long = 0
        while (isRun) {
            publishProgress(d)
            searchList(d.my)
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

    private suspend fun notifyResult() {
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
        postState(NeoState.Success)
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
            postState(NeoState.Success)
        }
    }

    private suspend fun publishProgress(d: DateUnit) {
        postState(
            NeoState.Message(
                String.format(
                    strings.format_search_date,
                    d.monthString, d.year
                )
            )
        )
    }

    private suspend fun searchInResults(reverseOrder: Boolean) {
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
                postState(NeoState.Message(String.format(strings.format_search_proc, p1)))
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
    private fun searchList(name: String) {
        pages.open(name)
        storage.open()
        var n = pages.year * 650 + pages.month * 50
        val cursor: Cursor = when (mode) {
            MODE_TITLES ->
                pages.searchTitle(helper.request)
            MODE_LINKS ->
                pages.searchLink(helper.request)
            else -> { //везде: 3 или 5 (по всем материалам или в Посланиях и Катренах)
                //фильтрация по 0 и 1 будет позже
                n = checkPages(n)
                pages.searchParagraphs(helper.request)
            }
        }
        if (!cursor.moveToFirst()) {
            cursor.close()
            if (Lib.getFileDB(name).exists()) return
            val d = DateUnit.putYearMonth(pages.year, pages.month)
            val row = ContentValues()
            row.put(
                Const.TITLE,
                String.format(strings.format_month_no_loaded, d.monthString, d.year)
            )
            row.put(Const.LINK, name)
            row.put(DataBase.ID, n)
            storage.insert(row)
            return
        }
        val iPar = cursor.getColumnIndex(DataBase.PARAGRAPH)
        val iID = cursor.getColumnIndex(DataBase.ID)
        var row: ContentValues? = null
        var id = -1
        var add = true
        val des = StringBuilder()
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
                    if (mode == MODE_EPISTLES)
                        add = !link.isPoem
                    else if (mode == MODE_POEMS)
                        add = link.isPoem
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

    private fun checkPages(startId: Int): Int {
        val links = pages.getLinksList()
        if (links.isEmpty())
            return startId
        var i = 0
        val all = links.size
        while (i < links.size) {
            if (pages.existsPage(links[i]))
                links.removeAt(i)
            else i++
        }
        if (links.size == all) {
            val d = DateUnit.putYearMonth(pages.year, pages.month)
            val row = ContentValues()
            row.put(
                Const.TITLE,
                String.format(strings.format_month_no_loaded, d.monthString, d.year)
            )
            row.put(Const.LINK, pages.name)
            row.put(DataBase.ID, startId)
            storage.insert(row)
            return startId
        }
        i = startId
        for (link in links) {
            val row = ContentValues()
            row.put(Const.TITLE, String.format(strings.format_page_no_loaded, link))
            row.put(Const.LINK, link)
            row.put(DataBase.ID, i)
            i++
            storage.insert(row)

        }
        return i
    }

    fun loadMonth(date: String) { //MM.yy
        loadDate = date
        if (checkConnect())
            load()
    }

    fun loadPage(link: String) {
        loadLink = link
        if (checkConnect())
            load()
    }

    private fun findInPage(link: String, id: Int): ListItem {
        var item = ListItem(link, link)
        if (mode == MODE_EPISTLES && link.isPoem)
            return item
        if (mode == MODE_POEMS && !link.isPoem)
            return item
        pages.open(link)
        val pageId = pages.getPageId(link)

        val curTitle = pages.getPageById(pageId)
        if (!curTitle.moveToFirst()) {
            curTitle.close()
            return item
        }
        val iTitle = curTitle.getColumnIndex(Const.TITLE)
        val title = pages.getPageTitle(curTitle.getString(iTitle), link)
        curTitle.close()
        item = ListItem(title, link)
        item.des = strings.not_found

        val cursor = pages.getParagraphs(pageId)
        if (!cursor.moveToFirst()) {
            cursor.close()
            return item
        }
        val iPar = cursor.getColumnIndex(DataBase.PARAGRAPH)
        var row: ContentValues? = null
        val des = StringBuilder()
        do {
            val par = cursor.getString(iPar)
            if (par.contains(helper.request)) {
                if (row == null) {
                    row = ContentValues()
                    row.put(Const.TITLE, title)
                    row.put(Const.LINK, link)
                    row.put(DataBase.ID, id)
                    helper.countMaterials++
                }
                des.append(getDes(par, helper.request))
            }
        } while (cursor.moveToNext())
        cursor.close()
        if (row != null) {
            item.des = des.toString()
            row.put(Const.DESCTRIPTION, item.des)
            storage.insert(row)
        }
        return item
    }

    private fun dateFromString(date: String): DateUnit {
        val month = date.substring(0, 2).toInt()
        val year = date.substring(3).toInt() + 2000
        return DateUnit.putYearMonth(year, month)
    }

    fun paging() = paging.run()

    override val pagingScope: CoroutineScope
        get() = scope

    override suspend fun postFinish() {
        postState(NeoState.Ready)
    }
}