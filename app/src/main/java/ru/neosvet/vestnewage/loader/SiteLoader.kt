package ru.neosvet.vestnewage.loader

import ru.neosvet.utils.Const
import ru.neosvet.vestnewage.fragment.SiteFragment
import java.io.BufferedReader
import java.io.FileReader
import java.util.regex.Pattern

class SiteLoader(private val file: String) : ListLoader {
    private val patternList = Pattern.compile("\\d{4}\\.html")

    override fun getLinkList(): List<String> {
        val list = mutableListOf<String>()
        if (file.contains(SiteFragment.NEWS))
            return list
        val br = BufferedReader(FileReader(file))
        var s: String? = br.readLine()
        while (s != null) {
            if (isNeedLoad(s)) {
                if (s.contains("@"))
                    list.add(s.substring(9))
                else list.add(s)
            }
            s = br.readLine()
        }
        br.close()
        return list
    }

    private fun isNeedLoad(link: String): Boolean {
        if (!link.contains(Const.HTML))
            return false
        if (link.contains("tolkovaniya") || link.contains("/") && link.length < 18)
            return false
        return !patternList.matcher(link).matches()
    }
}