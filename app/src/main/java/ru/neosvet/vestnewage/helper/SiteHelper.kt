package ru.neosvet.vestnewage.helper

import ru.neosvet.vestnewage.loader.basic.LinksProvider
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.viewmodel.SiteToiler
import java.io.BufferedReader
import java.io.FileReader
import java.util.regex.Pattern

class SiteHelper : LinksProvider {
    private val patternList: Pattern by lazy {
        Pattern.compile("\\d{4}\\.html")
    }

    private fun isNeedLoad(link: String): Boolean {
        if (!link.contains(Const.HTML))
            return false
        if (link.contains("tolkovaniya") || link.contains("/") && link.length < 18)
            return false
        return !patternList.matcher(link).matches()
    }

    override fun getLinkList(): List<String> {
        val list = mutableListOf<String>()
        list.add(Urls.News)
        val file = Lib.getFile(SiteToiler.MAIN)
        val br = BufferedReader(FileReader(file))
        br.forEachLine {
            if (isNeedLoad(it)) {
                if (it.contains("@"))
                    list.add(it.substring(9))
                else list.add(it)
            }
        }
        br.close()
        return list
    }
}