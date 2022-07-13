package ru.neosvet.vestnewage.utils

import android.app.Activity
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.view.dialog.CustomDialog
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileReader
import java.io.FileWriter

class WordsUtils {
    companion object {
        private const val GOD_WORDS = "/god_words"
        fun saveGodWords(words: String) {
            val bw = BufferedWriter(FileWriter(Lib.getFile(GOD_WORDS)))
            bw.write(words)
            bw.close()
        }
    }

    private var godWords: String = ""

    private fun getGodWords(): String {
        if (godWords.isNotEmpty())
            return godWords
        val f = Lib.getFile(GOD_WORDS)
        if (f.exists().not())
            return godWords
        val br = BufferedReader(FileReader(f))
        godWords = br.readLine()
        br.close()
        return godWords
    }

    fun showAlert(act: Activity, searchFun: (String) -> Unit) {
        val msg = getGodWords()
        val dialog = CustomDialog(act)
        dialog.setTitle(act.getString(R.string.god_words))
        dialog.setRightButton(act.getString(R.string.close)) { dialog.dismiss() }
        if (msg.isEmpty()) {
            dialog.setMessage(act.getString(R.string.yet_load))
        } else {
            dialog.setMessage(msg)
            dialog.setLeftButton(act.getString(R.string.find)) {
                searchFun.invoke(msg.trim('.'))
                dialog.dismiss()
            }
        }
        dialog.show(null)
    }
}