package ru.neosvet.vestnewage.utils

import android.app.Activity
import okhttp3.Request
import okhttp3.internal.http.promisesBody
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.NeoException
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.NetConst
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.view.dialog.MessageDialog
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileReader
import java.io.FileWriter
import java.io.InputStreamReader

class WordsUtils {
    companion object {
        private const val GOD_WORDS = "/god_words"
    }
    private var godWords: String = ""

    private fun saveGodWords(words: String) {
        godWords = words
        val bw = BufferedWriter(FileWriter(Files.file(GOD_WORDS)))
        bw.write(words)
        bw.close()
    }

    fun getGodWords(): String {
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
                else searchFun.invoke(msg.trim('.'))
                dialog.dismiss()
            }
        }
        dialog.show(null)
    }

    fun update() {
        if (Urls.isSiteCom) loadQuoteCom()
        else loadQuote()
    }

    private fun loadQuoteCom() {
        val request = Request.Builder()
            .url(Urls.QuoteCom)
            .addHeader(NetConst.USER_AGENT, App.context.packageName)
            .build()
        val client = NeoClient.createHttpClient()
        val response = client.newCall(request).execute()
        if (response.isSuccessful.not()) throw NeoException.SiteCode(response.code)
        if (response.promisesBody().not()) throw NeoException.SiteNoResponse()
        val inStream = response.body.byteStream()
        val br = BufferedReader(InputStreamReader(inStream, Const.ENCODING))
        var s = br.readLine()
        while (!s.contains("quote"))
            s = br.readLine()
        br.close()
        val i = s.indexOf("quote") + 7
        s = s.substring(i, s.indexOf("</div>", i)).replace(Const.BR, Const.N)
        saveGodWords(s)
    }

    private fun loadQuote() {
        val client = NeoClient()
        val br = BufferedReader(InputStreamReader(client.getStream(Urls.Quote)))
        var s = br.readLine()
        br.close()
        var i = s.indexOf("quoteBlock")
        i = s.indexOf("class", i)
        s = s.substring(i, s.indexOf("\\u003C/p", i))
        s = s.replace("\\u0022", "\"").replace("\\u0020", " ").replace("\\u003Cbr\\u003E", Const.N)
        val bytes = mutableListOf<Byte>()
        i = s.indexOf("\\u")
        var n = 0
        while (i > -1) {
            i += 2
            n = i + 2
            bytes.add(s.substring(i, n).toByte(16))
            bytes.add(s.substring(n, n + 2).toByte(16))
            n += 2
            i = s.indexOf("\\u", n)
            if (i > n) {
                for (b in s.substring(n, i)) {
                    bytes.add(0)
                    bytes.add(b.code.toByte())
                }
            }
        }
        if (n < s.length) {
            for (b in s.substring(n)) {
                bytes.add(0)
                bytes.add(b.code.toByte())
            }
        }
        s = String(bytes.toByteArray(), Charsets.UTF_16).substring(1)
        saveGodWords(s)
    }
}