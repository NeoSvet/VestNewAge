package ru.neosvet.vestnewage.helper

import ru.neosvet.vestnewage.data.CheckItem
import ru.neosvet.vestnewage.viewmodel.basic.MarkerStrings
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.view.list.CheckAdapter

class MarkerHelper(private val strings: MarkerStrings) {
    private var content = ""
    private var countPar = 0f
    val parsList = mutableListOf<CheckItem>()
    val colsList = mutableListOf<CheckItem>()

    fun setContent(s: String) {
        parsList.clear()
        countPar = 5f // имитация нижнего "колонтитула" страницы
        content = s
        if (s.isEmpty())  // страница не загружена...
            return
        val m = s.split(Const.NN)
        var i = 0
        while (i < m.size) {
            parsList.add(CheckItem(id = i, title = m[i]))
            i++
        }
        i = s.indexOf(Const.N)
        while (i > -1) {
            countPar++
            i = s.indexOf(Const.N, i + 1)
        }
    }

    fun checkedAllPars() {
        parsList.forEach { item ->
            item.isChecked = true
        }
    }

    fun checkPars(index: Int, isChecked: Boolean): Int {
        if (index == 0) {
            parsList.forEach { item ->
                item.isChecked = isChecked
            }
            return CheckAdapter.ACTION_UPDATE_ALL
        }
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
        return CheckAdapter.ACTION_NONE
    }

    fun checkCols(index: Int, isChecked: Boolean): Int {
        colsList[index].isChecked = isChecked
        return CheckAdapter.ACTION_NONE
    }

    fun getPosText(p: Float): String {
        var k = (countPar * p / 100f).toInt() + 1
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

    fun getParString(): String? {
        val s = StringBuilder()
        if (parsList[0].isChecked) {
            s.append(strings.pageEntirely)
        } else {
            s.append(strings.selectedPar)
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

    fun getColString(): String? {
        val s = StringBuilder(strings.selectedCollections)
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

    fun checkTitleCol(title: String): String? {
        if (title.contains(Const.COMMA))
            return strings.unusedDot
        colsList.forEach { item ->
            if (item.title == title)
                return strings.titleAlreadyUsed
        }
        return null
    }
}