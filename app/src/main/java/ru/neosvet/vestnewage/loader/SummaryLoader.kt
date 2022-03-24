package ru.neosvet.vestnewage.loader

import android.content.Context
import ru.neosvet.utils.Const
import ru.neosvet.vestnewage.helpers.LoaderHelper
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileReader
import java.io.FileWriter

class SummaryLoader: ListLoader {
    override fun getLinkList(context: Context): Int {
        val br = BufferedReader(FileReader(context.filesDir.toString() + Const.RSS))
        val file = LoaderHelper.getFileList(context)
        val bw = BufferedWriter(FileWriter(file))
        var s: String?
        var k = 0
        while (br.readLine() != null) { //title
            s = br.readLine() //link
            bw.write(s)
            bw.newLine()
            bw.flush()
            k++
            br.readLine() //des
            br.readLine() //time
        }
        bw.close()
        br.close()
        return k
    }
}