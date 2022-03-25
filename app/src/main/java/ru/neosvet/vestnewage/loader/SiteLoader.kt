package ru.neosvet.vestnewage.loader

import ru.neosvet.utils.Const
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.fragment.SiteFragment
import ru.neosvet.vestnewage.helpers.LoaderHelper
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileReader
import java.io.FileWriter
import java.util.regex.Pattern

class SiteLoader(private val file: String) : ListLoader {
    private val patternList = Pattern.compile("\\d{4}\\.html")

    override fun getLinkList(): Int {
        if (file.contains(SiteFragment.NEWS))
            return 0
        val br = BufferedReader(FileReader(file))
        val f = LoaderHelper.getFileList()
        val bw = BufferedWriter(FileWriter(f, true))
        var s: String
        var k = 0
        while (br.readLine().also { s = it } != null) {
            if (isNeedLoad(s)) {
                if (s.contains("@")) bw.write(s.substring(9)) else bw.write(s)
                bw.newLine()
                bw.flush()
                k++
            }
        }
        bw.close()
        br.close()
        return k
    }

    private fun isNeedLoad(link: String): Boolean {
        if (!link.contains(Const.HTML))
            return false
        if (link.contains("tolkovaniya") || link.contains("/") && link.length < 18)
            return false
        return !patternList.matcher(link).matches()
    }
}