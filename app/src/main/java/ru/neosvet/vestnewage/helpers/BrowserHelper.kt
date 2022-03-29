package ru.neosvet.vestnewage.helpers

import ru.neosvet.utils.Const

class BrowserHelper {
    var search: String = ""
        private set
    var place: List<String> = listOf()
        private set
    var index: Int = -1
        private set
    var prog: Int = -1
    var isSearch: Boolean = false
        private set

    fun setSearchString(s: String) {
        search = s
        place = if (s.contains(Const.NN))
            s.split(Const.NN)
        else listOf(s)
        isSearch = true
        prog = 0
        setSearchIndex(0)
    }

    fun setSearchIndex(i: Int) {
        index = i
    }

    fun getCurrentSearch() = place[index]

    fun clearSearch() {
        search = ""
        place = listOf()
        index = -1
        isSearch = false
    }

    fun prevSearch(): Boolean {
        if (index > -1) {
            if (--index == -1)
                index = place.size - 1
            return true
        }
        return false
    }

    fun nextSearch(): Boolean {
        if (index > -1) {
            if (++index == place.size)
                index = 0
            return true
        }
        return false
    }

    fun upProg() {
        prog++
    }

    fun downProg() {
        prog--
    }
}