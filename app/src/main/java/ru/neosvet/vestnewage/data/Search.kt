package ru.neosvet.vestnewage.data

import android.database.Cursor
import ru.neosvet.vestnewage.helper.SearchHelper
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.SearchEngine

enum class SearchScreen {
    EMPTY, DEFAULT, RESULTS
}

interface StorageSearchable {
    fun searchWhere(from: String, link: String?, where: String): Cursor
    fun searchParagraphs(link: String, operator: String, find: String): Cursor
    fun searchParagraphs(operator: String, find: String): Cursor
    fun searchTitle(link: String, operator: String, find: String): Cursor
    fun searchTitle(operator: String, find: String): Cursor
    fun searchLink(find: String): Cursor
}

data class SearchItem(
    val id: Int,
    var title: String,
    val link: String,
    var des: String = ""
) {
    var string: String
        get() = des.ifEmpty { title }
        set(value) {
            if (des.isEmpty()) title = value
            else des = value
        }
}


sealed class SearchRequest {
    data class Simple(
        val stringRaw: String,  //for equals
        val isLetterCase: Boolean, //for equals
        val string: String,
        val operator: String,
        val find: String
    ) : SearchRequest() {
        fun equals(request: String, isLetterCase: Boolean): Boolean =
            stringRaw == request && this.isLetterCase == isLetterCase
    }

    data class Advanced(
        val string: String,  //for equals
        val isLetterCase: Boolean,  //for equals
        val isEnding: Boolean,  //for equals
        var link: String? = null,  //for equals
        val where: String
    ) : SearchRequest() {
        fun equals(helper: SearchHelper): Boolean =
            string == helper.request && isLetterCase == helper.isLetterCase && isEnding == helper.isEnding &&
                    ((helper.mode != SearchEngine.MODE_TITLES && !where.startsWith(Const.TITLE)) ||
                            (helper.mode == SearchEngine.MODE_TITLES && where.startsWith(Const.TITLE)))
    }
}




