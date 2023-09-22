package ru.neosvet.vestnewage.network

import android.content.Intent
import android.net.Uri
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.data.NeoException
import ru.neosvet.vestnewage.utils.Lib
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileReader
import java.io.FileWriter
import java.io.InputStreamReader

object Urls {
    private var TIME = 1684586127068L
    private const val FILE = "/urls.txt"
    const val PROM_LINK = "Posyl-na-Edinenie.html"
    const val VREMYA_LINK = "Vremya-Posyla.html"
    const val PRECEPT_LINK = "doctrine:174-179"

    private val URL = arrayOf(
        "http://neosvet.ucoz.ru/", "http://neosvet.somee.com/", //URL 0 1
        "https://blagayavest.info/", "https://www.otkroveniya.com/", //SITE 2 3
        "https://doktrina.info/doktrina-sozdatelya/", ////DOCTRINE_SITE  4
        "https://t.me/Novosti_ot_SOZDATELYA/", //TELEGRAM_URL 5
        "doctrine/", "vna/doctrine/", //DOCTRINE_BASE 6 7
        "vna/posts/", "", //ADDITION 8 9
        "ads_vna.txt", "vna/ads.txt", //DEV_ADS 10 11
        "http://neosvet.ucoz.ru/vna/", "", //WEB_PAGE 12 13
        "print/", "", //PAGE 14 15
        "_content/BV/style-print.min.css", //STYLE 16
        "", //17 TODO style.com load from html main page (print version)?
        "rss.xml", "rss/", //RSS 18 19
        "AjaxData/Calendar", "", //QUOTE 20 21
        // $Y - year, %M - month, %P - year-2004
        "AjaxData/Calendar/%Y-%M.json", "ajax.php?m=%M&y=%P", //CALENDAR 22 23
        "print/poems/%Y.html", "", //POEMS 24 25
        "print/tolkovaniya.html", "", //EPISTLES 26 27
        "intforum.html", "", //NEWS 28 29
        "novosti.html", "", //ADS 30 31
        "databases_vna", "vna/databases" //DATABASES 32 33
    )

    @JvmStatic
    val isSiteCom: Boolean
        get() = if (isCom == null) getCom() else isCom!!
    private var isCom: Boolean? = null
    private const val COM_FILE = "/com"

    private fun getCom(): Boolean =
        Lib.getFile(COM_FILE).exists().also { isCom = it }

    fun setCom(value: Boolean) {
        isCom = value
        val f = Lib.getFile(COM_FILE)
        if (value) f.createNewFile()
        else if (f.exists()) f.delete()
    }

    val DoctrineSite: String
        get() = URL[4]

    val TelegramUrl: String
        get() = URL[5]

    //-------- from dev site ---------------------------------------------------------------

    val DevSite: String
        get() = (if (isSiteCom) URL[1] else URL[0]) + "vna/"

    val DoctrineBase: String
        get() = if (isSiteCom) URL[1] + URL[7].ifEmpty { URL[6] }
        else URL[0] + URL[6]

    val Addition: String
        get() = if (isSiteCom) URL[1] + URL[9].ifEmpty { URL[8] }
        else URL[0] + URL[8]

    val DevAds: String
        get() = if (isSiteCom) URL[1] + URL[11].ifEmpty { URL[10] }
        else URL[0] + URL[10]

    val Databases: String
        get() = if (isSiteCom) URL[1] + URL[33].ifEmpty { URL[32] }
        else URL[0] + URL[32]

    val WebPage: String
        get() = if (isSiteCom) URL[13].ifEmpty { URL[12] } else URL[12]

    //-------- from official site ---------------------------------------------------------------
    @JvmStatic
    val MainSite: String //for cabinet, for pages <2016
        get() = URL[3]

    val Site: String
        get() = if (isSiteCom) URL[3] else URL[2]

    val Host: String
        get() {
            val s = Site.substring(8)
            return s.substring(0, s.length - 1)
        }

    val Page: String
        get() = if (isSiteCom) URL[3] + URL[15].ifEmpty { URL[14] }
        else URL[2] + URL[14]

    val Rss: String
        get() {
            return (if (isSiteCom) URL[3] +
                    URL[19].ifEmpty { URL[18] }
            else URL[2] + URL[18]) + "?" + System.currentTimeMillis()
        }

    val News: String
        get() = if (isSiteCom) URL[29].ifEmpty { URL[28] } else URL[28]

    val Ads: String
        get() = if (isSiteCom) URL[3] + URL[31].ifEmpty { URL[30] }
        else URL[2] + URL[30]

    val Epistles: String
        get() = if (isSiteCom) URL[3] + URL[27].ifEmpty { URL[26] }
        else URL[2] + URL[26]

    val Style: String = URL[2] + URL[16]

    val Quote: String = URL[2] + URL[20]

    val QuoteCom: String = URL[3] + URL[21]

    fun getCalendar(month: Int, year: Int): String {
        var url = if (isSiteCom) URL[3] +
                URL[23].ifEmpty { URL[22] }
        else URL[2] + URL[22]
        url = url.replace("%M", month.toString())
        return if (url.contains("%P"))
            url.replace("%P", (year - 2004).toString())
        else
            url.replace("%Y", year.toString())
    }

    fun getPoems(year: Int): String {
        val url = if (isSiteCom) URL[3] +
                URL[25].ifEmpty { URL[24] }
        else URL[2] + URL[24]
        return url.replace("%Y", year.toString())
    }

    fun restore() {
        val file = Lib.getFile(FILE)
        if (file.exists().not()) return
        val br = BufferedReader(FileReader(file))
        var i = -1
        br.forEachLine {
            if (i == -1) TIME = it.toLong()
            else URL[i] = it
            i++
        }
        br.close()
    }

    fun update(client: NeoClient, isCom: Boolean = isSiteCom) {
        val url = (if (isCom) URL[1] else URL[0]) + "vna$FILE"
        try {
            val stream = client.getStream(url)
            val br = BufferedReader(InputStreamReader(stream), 1000)
            var s: String? = br.readLine()
            if (s == null || TIME == s.toLong()) {
                br.close()
                stream.close()
                if (s == null && isCom == isSiteCom)
                    update(client, !isSiteCom)
                return
            }
            TIME = s.toLong()
            s = br.readLine()
            var i = 0
            while (s != null) {
                if (s.isEmpty() || s[0] != '[') {
                    URL[i] = s
                    i++
                    if (i == URL.size) break
                }
                s = br.readLine()
            }
            br.close()
            stream.close()
            val file = Lib.getFile(FILE)
            if (file.exists()) file.delete()
            val wr = BufferedWriter(FileWriter(file))
            wr.appendLine(TIME.toString())
            URL.forEach {
                wr.appendLine(it)
            }
            wr.close()
        } catch (e: Exception) {
            if (e is NeoException && isCom == isSiteCom)
                update(client, !isSiteCom)
        }
    }

    fun openInBrowser(url: String) {
        val emptyBrowserIntent = Intent(Intent.ACTION_VIEW)
        emptyBrowserIntent.data = Uri.fromParts("http", "", null)
        val targetIntent = Intent(Intent.ACTION_VIEW)
        targetIntent.data = Uri.parse(url)
        targetIntent.selector = emptyBrowserIntent
        App.context.startActivity(targetIntent)
    }
}