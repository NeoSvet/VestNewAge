package ru.neosvet.vestnewage.helper

import ru.neosvet.vestnewage.data.CheckItem
import ru.neosvet.vestnewage.viewmodel.basic.MarkerStrings
import ru.neosvet.vestnewage.storage.MarkersStorage
import ru.neosvet.vestnewage.utils.Const

class MarkerHelper(private val strings: MarkerStrings) {
    enum class Type {
        NONE, PAR, POS, COL
    }

    private val formatPos: String = strings.sel_pos + "%.1f%%"
    var type = Type.NONE
    val parsList = mutableListOf<CheckItem>()
    val colsList = mutableListOf<CheckItem>()
    var content: String = ""
    var countPar: Int = 0

    var title: String = ""
    var des: String = ""
    var isPar: Boolean = true //else - isPos
    var sel: String = ""
    var cols: String = ""
    var posText: String = ""
        get() {
            if (field.isEmpty())
                field = getPosText()
            return field
        }
    var pos: Float = 0f
    var newPos: Float = 0f

    fun updateSel() {
        if (isPar) {
            val s = getParList()
            sel = if (s == null) {
                checkedAllPars()
                getParList() ?: ""
            } else s
        } else
            sel = String.format(formatPos, pos)
    }

    private fun checkedAllPars() {
        parsList.forEach { item ->
            item.isChecked = true
        }
    }

    fun checkPars(index: Int, isChecked: Boolean): Int {
        if (index > 0) {
            parsList[index].isChecked = isChecked
            if (isChecked.not() && parsList[0].isChecked) {
                parsList[0].isChecked = false
                return 0
            } else {
                var k = 0
                parsList.forEach { item ->
                    if (item.isChecked) k++
                }
                if (k == parsList.size - 1) {
                    parsList[0].isChecked = true
                    return 0
                }
            }
            return index
        }
        parsList.forEach { item ->
            item.isChecked = isChecked
        }
        return -1
    }

    fun checkCols(index: Int, isChecked: Boolean): Int {
        colsList[index].isChecked = isChecked
        return index
    }

    fun getPosText(p: Float = pos): String {
        var k = (countPar.toFloat() * p / 100f).toInt() + 1
        var u: Int
        var i = 0
        do {
            k--
            u = i
            i = content.indexOf(Const.N, u + 1)
        } while (k > 1 && i > -1)
        if (i > -1) i = content.indexOf(Const.N, i + 1)
        if (i > -1) i = content.indexOf(Const.N, i + 1)
        return if (i > -1)
            content.substring(u, i).trim()
        else content.substring(u).trim()
    }

    fun getParList(): String? {
        val s = StringBuilder()
        if (parsList[0].isChecked) {
            s.append(strings.page_entirely)
        } else {
            s.append(strings.sel_par)
            for (i in 1 until parsList.size) {
                if (parsList[i].isChecked) {
                    s.append(i)
                    s.append(", ")
                }
            }
            s.delete(s.length - 2, s.length)
            if (!s.toString().contains(":")) {
                return null //ни один абзац не выбран
            }
        }
        return s.toString()
    }

    fun setParList() {
        if (sel.contains("№")) {
            parsList.forEach { item ->
                item.isChecked = false
            }
            val s = sel.substring(sel.indexOf(":") + 2).replace(", ", Const.COMMA)
            s.split(Const.COMMA).forEach {
                parsList[it.toInt()].isChecked = true
            }
        } else {
            parsList.forEach { item ->
                item.isChecked = true
            }
        }
    }

    fun getColList(): String? {
        val s = StringBuilder(strings.sel_col)
        for (i in colsList.indices) {
            if (colsList[i].isChecked) {
                s.append(colsList[i].title)
                s.append(", ")
            }
        }
        s.delete(s.length - 2, s.length)
        return if (s.toString().contains(":")) s.toString()
        else null
    }

    fun setColList() {
        val s = MarkersStorage.closeList(
            cols.substring(strings.sel_col.length).replace(", ", Const.COMMA)
        )
        var t: String
        colsList.forEach { item ->
            t = MarkersStorage.closeList(item.title)
            item.isChecked = s.contains(t)
        }
    }

    fun setPlace(s: String) {
        if (s.contains("%")) {
            isPar = false
            pos = s.substring(0, s.length - 1).replace(Const.COMMA, ".").toFloat()
            posText = getPosText()
            sel = strings.sel_pos + s
        } else {
            isPar = true
            sel = if (s == "0")
                strings.page_entirely
            else
                strings.sel_par + s.replace(Const.COMMA, ", ")
            setParList()
        }
    }

    fun checkTitleCol(title: String): String? {
        if (title.contains(Const.COMMA))
            return strings.unuse_dot
        colsList.forEach { item ->
            if (item.title == title)
                return strings.title_already_used
        }
        return null
    }

    fun toJson(): String =
        "title{$title}des{$des}isPar{$isPar}sel{$sel}cols{$cols}posText{$posText}pos{$pos}newPos{$newPos}pars{${parsList.size}cols{${colsList.size}"
}