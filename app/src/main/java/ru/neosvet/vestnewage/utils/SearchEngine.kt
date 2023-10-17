package ru.neosvet.vestnewage.utils

import android.annotation.SuppressLint
import android.content.ContentValues
import android.database.Cursor
import ru.neosvet.vestnewage.data.*
import ru.neosvet.vestnewage.helper.SearchHelper
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.storage.SearchStorage
import ru.neosvet.vestnewage.viewmodel.basic.SearchStrings
import java.util.*

class SearchEngine(
    private val storage: SearchStorage,
    private val pages: PageStorage,
    private val helper: SearchHelper,
    private val parent: Parent
) {
    interface Parent {
        val strings: SearchStrings
        suspend fun notifyResult()
        suspend fun notifyPercent(percent: Int)
        suspend fun notifyDate(date: DateUnit)
        fun clearLast()
        suspend fun notifyNotFound()
        suspend fun searchFinish()
    }

    companion object {
        private const val MODE_EPISTLES = 0
        private const val MODE_POEMS = 1
        const val MODE_TITLES = 2
        private const val MODE_ALL = 3
        const val MODE_LINKS = 4
        const val MODE_BOOK = 5
        const val MODE_DOCTRINE = 6
        const val MODE_RESULT_TEXT = 7
        const val MODE_RESULT_PAR = 8
        private const val OR = " OR "
        private const val startSelect = "<font color='#99ccff'><b>"
        private const val endSelect = "</b></font>"
        private const val lenSelect = 36
        private const val startLetter = 65
        private const val endLetter = 2000
    }

    private fun String.isNotLetter(position: Int): Boolean {
        if (position < 0 || position >= length)
            return true
        val b = this[position].code
        return b < startLetter || b > endLetter
    }

    var countMatches = 0
    private set
    var countMaterials = 0
        private set
    private var prevMaterials = 0
    private var words = mutableListOf<String>()
    var endings: Array<String>? = null
    private var request: SearchRequest? = null
    var mode = 0
        private set
    private var isRun = true

    fun stop() {
        isRun = false
    }

    suspend fun startSearch(mode: Int) {
        helper.isNeedLoad = false
        this.mode = mode
        countMatches = 0
        countMaterials = 0
        storage.open()
        isRun = true
        when (mode) {
            MODE_RESULT_TEXT -> searchInResultText()
            MODE_RESULT_PAR -> searchInResultPar()
            else -> searchInPages()
        }
        pages.close()
        parent.searchFinish()
    }

    suspend fun startSearch(list: String) {
        helper.isNeedLoad = false
        searchList(list)
        pages.close()
        parent.searchFinish()
    }

    private suspend fun searchList(name: String) {
        pages.open(name)
        storage.open()
        storage.isDesc = helper.isDesc
        val id = pages.year * 650 + pages.month * 50
        val list = getResultList(id, null)
        if (list.isEmpty() && checkMonth(id, pages.name))
            return
        notifyResultIfNeed()
        listToStorage(list)
    }

    private suspend fun searchInPages() = helper.run {
        storage.clear()
        if (mode == MODE_DOCTRINE) {
            searchList(DataBase.DOCTRINE)
            pages.close()
            return@run
        }
        if (mode == MODE_ALL)
            searchList(DataBase.ARTICLES)
        val step: Int
        val finish: Int
        val d = if (isDesc) {
            step = -1
            finish = start.timeInDays
            DateUnit.putYearMonth(end.year, end.month)
        } else {
            step = 1
            finish = end.timeInDays
            DateUnit.putYearMonth(start.year, start.month)
        }

        while (isRun) {
            parent.notifyDate(d)
            searchList(d.my)
            if (d.timeInDays == finish) break
            d.changeMonth(step)
        }
        pages.close()
    }

    private suspend fun notifyResultIfNeed() {
        if (prevMaterials == countMaterials) return
        parent.notifyResult()
        prevMaterials = countMaterials
    }

    private suspend fun searchInResultText() {
        val links = mutableListOf<String>()
        val cursor = storage.getResults(helper.isDesc)
        if (cursor.moveToFirst()) {
            val iLink = cursor.getColumnIndex(Const.LINK)
            do {
                links.add(cursor.getString(iLink))
            } while (cursor.moveToNext())
        }
        cursor.close()
        storage.clear()

        parent.clearLast()
        var n = 1
        for (i in links.indices) {
            if (!isRun) break
            pages.open(links[i])
            val list = getResultList(n, links[i])
            if (list.isNotEmpty()) {
                listToStorage(list)
                n += list.size
            }
            parent.notifyPercent(i.percent(links.size))
        }
        pages.close()
        links.clear()
    }

    private suspend fun searchInResultPar() {
        val items = mutableListOf<BasicItem>()
        val cursor = storage.getResults(helper.isDesc)
        if (cursor.moveToFirst()) {
            val iDes = cursor.getColumnIndex(Const.DESCTRIPTION)
            val iTitle = cursor.getColumnIndex(Const.TITLE)
            val iLink = cursor.getColumnIndex(Const.LINK)
            do {
                val d = cursor.getString(iDes)
                if (d.isNullOrEmpty().not() && d.contains("<")) {
                    val item = BasicItem(cursor.getString(iTitle), cursor.getString(iLink)).apply {
                        des = d.fromHTML
                    }
                    items.add(item)
                }
            } while (cursor.moveToNext())
        }
        cursor.close()
        if (items.isEmpty()) {
            searchInResultText()
            return
        }
        storage.clear()

        parent.clearLast()
        var n = 1
        var u: Int
        for (i in items.indices) {
            if (!isRun) break
            val item = items[i]
            pages.open(item.link)
            val list = getResultList(n, item.link).toMutableList()
            u = 0
            while (u < list.size) when {
                item.des.contains(list[u].des.fromHTML) -> u++
                u + 1 < list.size && list[u].id == list[u + 1].id -> {
                    list[u].des = list[u + 1].des
                    list.removeAt(u + 1)
                }

                else -> list.removeAt(u)
            }
            if (list.isNotEmpty()) {
                listToStorage(list)
                n += list.size
            }
            parent.notifyPercent(i.percent(items.size))
        }
        pages.close()
        items.clear()
    }

    private suspend fun searchInLink(startId: Int, link: String?): List<SearchItem> {
        val list = when {
            link == null ->
                titleToList(pages.searchLink(helper.request), startId, false)

            link.contains(helper.request) ->
                listOf(SearchItem(startId, pages.getTitle(link), link))

            else ->
                listOf()
        }
        countMaterials += list.size
        list.forEach {
            it.des = getSelected(it.link, helper.request)
        }
        notifyResultIfNeed()
        return list
    }

    private suspend fun simpleSearch(
        isPar: Boolean,
        link: String?,
        startId: Int
    ): List<SearchItem> {
        request?.let { r ->
            if (r is SearchRequest.Advanced) {
                words.clear()
                request = null
            } else if (r is SearchRequest.Simple &&
                !r.equals(helper.request, helper.isLetterCase)
            ) request = null
        }
        val r: SearchRequest.Simple
        if (request == null) {
            val string = preparingString()
            val p = if (helper.isLetterCase)
                Pair(DataBase.GLOB, "*${string}*")
            else
                Pair(DataBase.LIKE, "%${string}%")
            r = SearchRequest.Simple(
                stringRaw = helper.request,
                string = string,
                isLetterCase = helper.isLetterCase,
                operator = p.first,
                find = p.second
            )
            request = r
        } else
            r = request as SearchRequest.Simple
        val list = if (isPar) {
            val cursor = link?.let {
                pages.searchParagraphs(it, r.operator, r.find)
            } ?: pages.searchParagraphs(r.operator, r.find)
            parToList(cursor, startId, true)
        } else {
            val cursor = link?.let {
                pages.searchTitle(it, r.operator, r.find)
            } ?: pages.searchTitle(r.operator, r.find)
            titleToList(cursor, startId, true)
        }
        countMaterials += list.size
        if (isPar) list.forEach {
            if (it.link.isEmpty()) countMaterials--
        }
        notifyResultIfNeed()
        return list
    }

    private fun preparingString(): String {
        var s = helper.request
        var i = 0
        while (i < s.length) {
            if (s[i] == '"') {
                s = if (i == 0 || s.isNotLetter(i - 1))
                    s.substring(0, i) + "&laquo;" + s.substring(i + 1)
                else
                    s.substring(0, i) + "&raquo;" + s.substring(i + 1)
                i += 7
            } else i++
        }
        s = s.replace(" - ", " &ndash; ").replace("–", "&ndash;")
            .replace("«", "&laquo;").replace("»", "&raquo;")
        //how/need add support “ and ” ?
        return s
    }

    private suspend fun advancedSearch(
        isPar: Boolean,
        link: String?,
        startId: Int
    ): List<SearchItem> {
        request?.let { r ->
            if (r is SearchRequest.Simple || (r is SearchRequest.Advanced && !r.equals(helper)))
                request = null
        }
        val name = if (isPar) DataBase.PARAGRAPH else Const.TITLE
        val r: SearchRequest.Advanced
        if (request == null) {
            val s = helper.request
            words = if (helper.isEnding)
                getWords(s) else removeEnding(getWords(s))
            if (words.isEmpty()) {
                parent.notifyNotFound()
                return listOf()
            }
            removeDuplicateWords()
            val format = if (helper.isLetterCase)
                DataBase.GLOB.substring(0, 6) + "'*%s*'"
            else
                DataBase.LIKE.substring(0, 6) + "'%%%s%%'"
            val sb = StringBuilder()
            words.forEach {
                sb.append(OR)
                sb.append(name)
                sb.append(format.format(it))
            }
            sb.delete(0, OR.length)

            r = SearchRequest.Advanced(
                string = helper.request,
                isLetterCase = helper.isLetterCase,
                isEnding = helper.isEnding,
                whereRaw = sb.toString()
            )
            r.where = r.whereRaw
            request = r
        } else
            r = request as SearchRequest.Advanced
        if (r.link != link) {
            r.link = link
            r.where = link?.let {
                "${DataBase.ID}=${pages.getPageId(it)} AND (" + r.whereRaw + ")"
            } ?: r.whereRaw
        }

        val cursor = pages.rawQuery(name, r.where)
        val list = if (name == Const.TITLE)
            titleToList(cursor, startId, false)
        else parToList(cursor, startId, false)

        return filterList(list)
    }

    private suspend fun filterList(list: MutableList<SearchItem>): List<SearchItem> {
        var isEnd = true
        var isPre = true
        var k: Int
        var x: Int
        var i: Int
        var n = 0
        var id = -1
        val con = BooleanArray(if (helper.isAllWords) words.size else 0)
        while (n < list.size) {
            val sb = StringBuilder(list[n].string)
            if (helper.isAllWords && id != list[n].id) {
                //если в прошлом тексте были не все слова, то удаляем
                if (n > 0 && con.contains(false)) {
                    n--
                    while (n > -1 && list[n].id == id) {
                        list.removeAt(n)
                        n--
                    }
                    n++
                }
                Arrays.fill(con, false) //очищаем для нового текста
                id = list[n].id
            }
            k = 0
            words.forEach { //перебор слов
                x = 0
                val s: String
                val w = if (helper.isLetterCase) {
                    s = sb.toString()
                    it
                } else {
                    s = sb.toString().lowercase()
                    it.lowercase()
                }
                i = s.indexOf(w, 0)
                while (i > -1) { //перебор совпадений
                    if (helper.isPrefix)
                        isPre = s.isNotLetter(i - 1)
                    if (helper.isEnding && isPre)
                        isEnd = s.isNotLetter(i + w.length)
                    if (isEnd && isPre) {
                        sb.insert(i + x + w.length, endSelect)
                        sb.insert(i + x, startSelect)
                        x += lenSelect
                        if (k < con.size) con[k] = true
                    }
                    i = s.indexOf(w, i + w.length)
                } //end перебор совпадений
                k++
            } //end перебор слов
            when {
                sb.contains(endSelect) -> {
                    list[n].string = sb.toString()
                    n++
                }

                n + 1 < list.size && id == list[n + 1].id -> {
                    list[n].des = list[n + 1].des
                    list.removeAt(n + 1)
                }

                else -> list.removeAt(n)
            }
        }
        if (helper.isAllWords && n > 0 && con.contains(false)) {
            n--
            while (n > -1 && list[n].id == id) {
                list.removeAt(n)
                n--
            }
        }
        countMaterials += list.size
        when (mode) {
            MODE_TITLES -> list.forEach {
                countMatches += calcSelected(it.title)
            }

            else -> list.forEach {
                if (it.link.isEmpty()) countMaterials--
                countMatches += calcSelected(it.des)
            }
        }
        notifyResultIfNeed()
        return list
    }

    private fun calcSelected(s: String): Int {
        var k = 0
        var i = s.indexOf(startSelect)
        while (i > -1) {
            k++
            i = s.indexOf(startSelect, i + lenSelect)
        }
        return k
    }

    private fun getWords(r: String): MutableList<String> {
        val sb = StringBuilder()
        val list = mutableListOf<String>()
        r.replace(" - ", " ").forEach {
            if (it == ' ') {
                if (sb.isNotEmpty() && sb.length > 2)
                    list.add(sb.toString())
                sb.clear()
            } else if (it == '-' || it.code in (startLetter until endLetter))
                sb.append(it)
        }
        if (sb.isNotEmpty() && sb.length > 2)
            list.add(sb.toString())
        return list
    }

    private fun removeDuplicateWords() {
        var i = 0
        var n: Int
        while (i < words.size) {
            n = i + 1
            while (n < words.size) {
                if (words[i] == words[n])
                    words.removeAt(n)
                else n++
            }
            i++
        }
    }

    private fun removeEnding(w: MutableList<String>): MutableList<String> {
        var i: Int
        for (n in w.indices) {
            endings?.forEach { e ->
                i = w[n].length - e.length
                if (i > 2 && w[n].lastIndexOf(e, ignoreCase = true) == i) {
                    w[n] = w[n].substring(0, i)
                    return@forEach
                }
            }
        }
        return w
    }

    private fun selectWords(text: String): String {
        var t = text
        if (words.isEmpty())
            t = getSelected(t, (request as SearchRequest.Simple).string)
        else words.forEach {
            t = getSelected(t, it)
        }
        return t
    }

    private fun getSelected(text: String, sel: String): String {
        val sb = StringBuilder(text)
        val t: String
        val s: String
        if (helper.isLetterCase) {
            t = text
            s = sel
        } else {
            t = text.lowercase()
            s = sel.lowercase()
        }
        var i = t.indexOf(s)
        var x = 0
        while (i > -1) {
            sb.insert(i + x + sel.length, endSelect)
            sb.insert(i + x, startSelect)
            x += lenSelect
            countMatches++
            i = t.indexOf(s, i + sel.length)
        }
        return sb.toString()
    }

    private fun listToStorage(list: List<SearchItem>) {
        var id = -1
        val des = StringBuilder()
        var row: ContentValues? = null
        list.forEach { item ->
            if (id != item.id) {
                row?.let {
                    if (des.isNotEmpty())
                        it.put(Const.DESCTRIPTION, des.toString())
                    storage.insert(it)
                }
                id = item.id
                des.clear()
                row = ContentValues().apply {
                    put(DataBase.ID, item.id)
                    put(Const.TITLE, item.title)
                    put(Const.LINK, item.link)
                }
            }
            if (item.des.isNotEmpty())
                des.append(item.des)
        }
        row?.let {
            if (des.isNotEmpty())
                it.put(Const.DESCTRIPTION, des.toString())
            storage.insert(it)
        }
    }

    private suspend fun getResultList(startId: Int, link: String?): List<SearchItem> {
        return when (mode) {
            MODE_TITLES -> {
                val n = checkTitles(startId)
                if (helper.isByWords)
                    advancedSearch(false, link, n)
                else
                    simpleSearch(false, link, n)
            }

            MODE_LINKS -> searchInLink(startId, link)
            else -> { //везде: 3 или 5 (по всем материалам или в Посланиях и Катренах)
                //фильтрация по 0 и 1 будет позже
                val n = checkPages(startId)
                if (helper.isByWords)
                    advancedSearch(true, link, n)
                else
                    simpleSearch(true, link, n)
            }
        }
    }

    @SuppressLint("Range")
    private fun parToList(
        cursor: Cursor,
        startId: Int,
        isSelect: Boolean
    ): MutableList<SearchItem> {
        if (!cursor.moveToFirst()) {
            cursor.close()
            return mutableListOf()
        }
        val iPar = cursor.getColumnIndex(DataBase.PARAGRAPH)
        val iID = cursor.getColumnIndex(DataBase.ID)
        var id = -1
        var n = startId - 1
        var add = true
        val list = mutableListOf<SearchItem>()
        do {
            if (id == cursor.getInt(iID) && add) {
                val item = SearchItem(n, "", "")
                item.des = if (isSelect)
                    selectWords(cursor.getString(iPar))
                else
                    cursor.getString(iPar)
                list.add(item)
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
                        n++
                        val item = SearchItem(n, title, link)
                        item.des = if (isSelect)
                            selectWords(cursor.getString(iPar))
                        else
                            cursor.getString(iPar)
                        list.add(item)
                    }
                }
                curTitle.close()
            }
        } while (cursor.moveToNext())
        cursor.close()
        return list
    }

    private fun titleToList(
        cursor: Cursor,
        startId: Int,
        isSelect: Boolean
    ): MutableList<SearchItem> {
        if (!cursor.moveToFirst()) {
            cursor.close()
            return mutableListOf()
        }
        val iLink = cursor.getColumnIndex(Const.LINK)
        val iTitle = cursor.getColumnIndex(Const.TITLE)
        val list = mutableListOf<SearchItem>()
        var add = true
        var id = startId
        do {
            val link = cursor.getString(iLink)
            if (mode == MODE_EPISTLES)
                add = !link.isPoem
            else if (mode == MODE_POEMS)
                add = link.isPoem
            if (add) {
                val title = pages.getPageTitle(cursor.getString(iTitle), link)
                val item = SearchItem(id, title, link)
                if (isSelect)
                    item.title = selectWords(item.title)
                list.add(item)
                id++
            }
        } while (cursor.moveToNext())
        cursor.close()
        return list
    }

    private fun checkMonth(id: Int, date: String): Boolean {
        val f = Files.dateBase(date)
        if (f.exists() && f.length() > DataBase.EMPTY_BASE_SIZE)
            return false
        helper.isNeedLoad = true
        val d = DateUnit.putYearMonth(pages.year, pages.month)
        val row = ContentValues()
        row.put(
            Const.TITLE,
            String.format(parent.strings.format_month_no_loaded, d.monthString, d.year)
        )
        row.put(Const.LINK, date)
        row.put(DataBase.ID, id)
        storage.insert(row)
        return true
    }

    private fun checkTitles(startId: Int): Int {
        val cursor = pages.getListAll()
        if (!cursor.moveToFirst() || !cursor.moveToNext()) {
            cursor.close()
            return startId
        }
        val iTitle = cursor.getColumnIndex(Const.TITLE)
        val iLink = cursor.getColumnIndex(Const.LINK)
        var i = startId
        do {
            val link = cursor.getString(iLink)
            val title = cursor.getString(iTitle)
            if (title == link) {
                helper.isNeedLoad = true
                val row = ContentValues()
                row.put(Const.TITLE, String.format(parent.strings.format_page_no_loaded, link))
                row.put(Const.LINK, link)
                row.put(DataBase.ID, i)
                storage.insert(row)
                i++
            }
        } while (cursor.moveToNext())
        cursor.close()
        return i
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
        helper.isNeedLoad = true
        if (links.size == all) {
            val d = DateUnit.putYearMonth(pages.year, pages.month)
            val row = ContentValues()
            row.put(
                Const.TITLE,
                String.format(parent.strings.format_month_no_loaded, d.monthString, d.year)
            )
            row.put(Const.LINK, pages.name)
            row.put(DataBase.ID, startId)
            storage.insert(row)
            return startId
        }
        i = startId
        for (link in links) {
            val row = ContentValues()
            row.put(Const.TITLE, String.format(parent.strings.format_page_no_loaded, link))
            row.put(Const.LINK, link)
            row.put(DataBase.ID, i)
            i++
            storage.insert(row)
        }
        return i
    }

    suspend fun findInPage(link: String, id: Int): BasicItem {
        var item = BasicItem(link, link)
        if (mode == MODE_EPISTLES && link.isPoem)
            return item
        if (mode == MODE_POEMS && !link.isPoem)
            return item
        pages.open(link)
        val list = getResultList(id, link)

        if (list.isEmpty()) {
            item = BasicItem(pages.getTitle(link), link)
            item.des = parent.strings.not_found
        } else {
            listToStorage(list)
            val des = StringBuilder()
            item = BasicItem(list[0].title, link)
            list.forEach {
                des.append(it.des)
            }
            item.des = des.toString()
        }

        pages.close()
        parent.searchFinish()
        return item
    }
}