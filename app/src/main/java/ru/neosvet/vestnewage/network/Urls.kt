package ru.neosvet.vestnewage.network

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.NeoException
import ru.neosvet.vestnewage.utils.Files
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileReader
import java.io.FileWriter
import java.io.InputStreamReader

object Urls {
    private var TIME = 1753423874000L
    private const val FILE = "/urls.txt"
    const val PRED_LINK = "/2004/predislovie.html"
    const val DOCTRINE = "https://doktrina.info/"
    const val ACADEMY = "https://akegn.ru"
    const val PROM_LINK = "Posyl-na-Edinenie.html"
    const val PRECEPT_LINK = "doctrine:174-179"

    private val URL = arrayOf(
        "http://neosvet.ucoz.ru/", "http://neosvet.somee.com/", //URL 0 1
        "https://blagayavest.info/", "https://www.otkroveniya.com/", //SITE 2 3
        "https://doktrina.info/doktrina-sozdatelya/", //DOCTRINE_SITE  4
        "https://t.me/Novosti_ot_SOZDATELYA/", //TELEGRAM_URL 5
        "doctrine/", "vna/doctrine/", //DOCTRINE_BASE 6 7
        "vna/posts/", "", //ADDITION 8 9
        "ads_vna.txt", "vna/ads.txt", //DEV_ADS 10 11
        "http://neosvet.ucoz.ru/vna/", "", //WEB_PAGE 12 13
        "print/", "", //PAGE 14 15
        "_content/BV/style-print.min.css", //STYLE 16
        "", //17 TODO style.com load from html main page (print version)?
        "rss.xml", "", //RSS 18 19
        "AjaxData/Calendar", "", //QUOTE 20 21
        // $Y - year, %M - month, %P - year-2004
        "AjaxData/Calendar/%Y-%M.json", "", //CALENDAR 22 23
        "print/poems/%Y.html", "", //POEMS 24 25
        "print/tolkovaniya.html", "", //EPISTLES 26 27
        "intforum.html", "", //NEWS 28 29
        "novosti.html", "", //ADS 30 31
        "databases_vna", "vna/databases", //DATABASES 32 33
        "vna/svyataya-rus/", "", //HOLY_RUS_BASE 34 35
        "https://doktrina.info/svyataya-rus/",  //HOLY_RUS_SITE 36
        "vna/world-after-war/", "", //WORLD_AFTER_WAR_BASE 37 38
        "https://doktrina8.ru/mir.html"  //WORLD_AFTER_WAR_SITE 39
    )

    @JvmStatic
    val isSiteCom: Boolean
        get() = if (isCom == null) getCom() else isCom!!
    private var isCom: Boolean? = null
    private const val COM_FILE = "/com"

    private fun getCom(): Boolean =
        Files.file(COM_FILE).exists().also { isCom = it }

    fun setCom(value: Boolean) {
        isCom = value
        val f = Files.file(COM_FILE)
        if (value) f.createNewFile()
        else if (f.exists()) f.delete()
    }

    val DoctrineSite: String
        get() = URL[4]

    val HolyRusSite: String
        get() = URL[36]

    val WorldAfterWarSite: String
        get() = URL[39]

    val TelegramUrl: String
        get() = URL[5]

    //-------- from dev site ---------------------------------------------------------------

    val DevSite: String
        get() = (if (isSiteCom) URL[1] else URL[0]) + "vna/"

    val DoctrineBase: String
        get() = if (isSiteCom) URL[1] + URL[7].ifEmpty { URL[6] }
        else URL[0] + URL[6]

    val HolyRusBase: String
        get() = if (isSiteCom) URL[1] + URL[35].ifEmpty { URL[34] }
        else URL[0] + URL[34]

    val WorldAfterWarBase: String
        get() = if (isSiteCom) URL[1] + URL[38].ifEmpty { URL[37] }
        else URL[0] + URL[37]

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
    val MainSite: String //for pages <2016 year
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

    val RSS: String
        get() {
            return (if (isSiteCom) URL[3].ifEmpty { URL[2] } else URL[2]) +
                    URL[18] + "?" + System.currentTimeMillis()
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

    fun getCalendar(month: Int, year: Int): String {
        var url = (if (isSiteCom) URL[3].ifEmpty { URL[2] } else URL[2]) + URL[22]
        url = url.replace("%M", month.toString())
        return url.replace("%Y", year.toString())
    }

    fun getPoems(year: Int): String {
        val url = if (isSiteCom) URL[3] +
                URL[25].ifEmpty { URL[24] }
        else URL[2] + URL[24]
        return url.replace("%Y", year.toString())
    }

    fun restore() {
        val file = Files.file(FILE)
        if (file.exists().not()) return
        val br = BufferedReader(FileReader(file))
        val t = br.readLine().toLong()
        if (t > TIME) {
            TIME = t
            var i = 0
            br.forEachLine {
                URL[i] = it
                i++
            }
        } else file.delete()
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
            val file = Files.file(FILE)
            if (file.exists()) file.delete()
            val bw = BufferedWriter(FileWriter(file))
            bw.appendLine(TIME.toString())
            URL.forEach { bw.appendLine(it) }
            bw.close()
        } catch (e: Exception) {
            if (e is NeoException && isCom == isSiteCom)
                update(client, !isSiteCom)
        }
    }

    @JvmStatic
    fun openInBrowser(url: String) {
        val emptyBrowserIntent = Intent(Intent.ACTION_VIEW)
        emptyBrowserIntent.data = Uri.fromParts("http", "", null)
        val targetIntent = Intent(Intent.ACTION_VIEW)
        targetIntent.data = url.toUri()
        targetIntent.selector = emptyBrowserIntent
        targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        App.context.startActivity(targetIntent)
    }

    @JvmStatic
    fun openInApps(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = url.toUri()
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            App.context.startActivity(intent)
        } catch (_: java.lang.Exception) {
            var s = url.substring(url.indexOf(":") + 1)
            if (s.startsWith("/")) s = s.substring(2)
            copyAddress(s)
        }
    }

    @JvmStatic
    fun copyAddress(txt: String?) {
        val clipboard = App.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(App.context.getString(R.string.app_name), txt)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(
            App.context,
            App.context.getString(R.string.address_copied),
            Toast.LENGTH_LONG
        ).show()
    }
}