package ru.neosvet.vestnewage.utils

import android.app.Activity
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.view.dialog.MessageDialog
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileReader
import java.io.FileWriter

object WordsUtils {
    private const val GOD_WORDS = "/god_words"
    private var godWords: String = ""

    fun saveGodWords(words: String) {
        godWords = words
        val bw = BufferedWriter(FileWriter(Files.file(GOD_WORDS)))
        bw.write(words)
        bw.close()
    }

    private fun getGodWords(): String {
        if (godWords.isNotEmpty())
            return godWords
        val f = Files.file(GOD_WORDS)
        if (f.exists().not())
            return godWords
        val br = BufferedReader(FileReader(f))
        godWords = br.readText()
        br.close()
        return godWords
    }

    fun showAlert(act: Activity, searchFun: (String) -> Unit) {
        val msg = getGodWords()
        val dialog = MessageDialog(act)
        dialog.setTitle(act.getString(R.string.god_words))
        dialog.setRightButton(act.getString(R.string.close)) { dialog.dismiss() }
        if (msg.isEmpty()) {
            dialog.setMessage(act.getString(R.string.yet_load))
        } else {
            dialog.setMessage(msg)
            dialog.setLeftButton(act.getString(R.string.find)) {
                if (msg.contains(Const.N))
                    searchFun.invoke(msg.substring(0, msg.indexOf(Const.N)).trim('.'))
                else
                    searchFun.invoke(msg.trim('.'))
                dialog.dismiss()
            }
        }
        dialog.show(null)
    }
}