package ru.neosvet.vestnewage.loader.page

import okhttp3.Request
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.NetConst
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.utils.Files
import java.io.*

class StyleLoader {
    companion object {
        const val LIGHT = "/style/light.css"
        const val DARK = "/style/dark.css"
        private const val FONT_STRING =
            "@font-face{font-family:\"MyriadProCondensed\";src: url(\"myriad.ttf\") format(\"truetype\")} "
    }

    private val builderRequest = Request.Builder()
    private val client = NeoClient.createHttpClient()
    private val fLight = Files.link(LIGHT)
    private val fDark = Files.link(DARK)

    init {
        builderRequest.header(NetConst.USER_AGENT, App.context.packageName)
        builderRequest.header(NetConst.REFERER, Urls.Host)
    }

    fun download(replaceStyle: Boolean) {
        if (!fLight.exists() || !fDark.exists() || replaceStyle) {
            if (downloadStyle().not())
                downloadStyleFromSite()
        }
    }

    private fun downloadFile(url: String, file: File): Boolean {
        builderRequest.url(url + file.name)
        val response = client.newCall(builderRequest.build()).execute()
        if (response.isSuccessful.not()) return false
        val br = BufferedReader(response.body.charStream(), 1000)
        val bw = BufferedWriter(OutputStreamWriter(FileOutputStream(file)))
        bw.write(br.readLine())
        br.close()
        bw.close()
        return true
    }

    private fun downloadStyle() =
        downloadFile(Urls.DevSite, fLight) &&
                downloadFile(Urls.DevSite, fDark)

    private fun downloadStyleFromSite() {
        builderRequest.url(Urls.Style)
        val response = client.newCall(builderRequest.build()).execute()
        val br = BufferedReader(response.body.charStream(), 1000)
        val bwLight = BufferedWriter(OutputStreamWriter(FileOutputStream(fLight)))
        val bwDark = BufferedWriter(OutputStreamWriter(FileOutputStream(fDark)))
        var s = br.readLine()
        br.close()
        response.close()
        var u: Int
        val m = s.split("#".toRegex())
        bwLight.write(FONT_STRING)
        bwDark.write(FONT_STRING)
        bwLight.flush()
        bwDark.flush()
        for (i in 1 until m.size) {
            s = if (i == 1) m[i].substring(m[i].indexOf("body")) else "#" + m[i]
            if (s.contains("P B {")) { //correct bold
                u = s.indexOf("P B {")
                s = s.substring(0, u) + s.substring(s.indexOf("}", u) + 1)
            }
            if (s.contains("content")) s = s.replace("15px", "5px")
            else if (s.contains("print2")) s = s.replace("8pt/9pt", "12pt")
            bwLight.write(s)
            s = s.replace("#333", "#ccc")
            s = if (s.contains("#000")) s.replace("#000", "#fff")
            else s.replace("#fff", "#000")
            bwDark.write(s)
            bwLight.flush()
            bwDark.flush()
        }
        bwLight.close()
        bwDark.close()
    }
}