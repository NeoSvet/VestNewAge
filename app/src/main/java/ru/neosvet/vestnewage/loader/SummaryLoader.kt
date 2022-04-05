package ru.neosvet.vestnewage.loader

import ru.neosvet.utils.Const
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.helpers.LoaderHelper
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileReader
import java.io.FileWriter

class SummaryLoader: ListLoader {
    override fun getLinkList(): List<String> {
        val br = BufferedReader(FileReader(App.context.filesDir.toString() + Const.RSS))
        val list = mutableListOf<String>()
        while (br.readLine() != null) { //title
            val link = br.readLine() //link
            list.add(link)
            br.readLine() //des
            br.readLine() //time
        }
        br.close()
        return list
    }
}