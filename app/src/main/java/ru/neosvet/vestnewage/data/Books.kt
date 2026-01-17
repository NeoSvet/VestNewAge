package ru.neosvet.vestnewage.data

import okhttp3.OkHttpClient
import okhttp3.Request
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.NetConst
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.DataBase
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Files
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

object Books {
    fun linkToBook(link: String) = when {
        link.contains(Const.HOLY_RUS) -> BookTab.HOLY_RUS
        link.contains(Const.WORLD_AFTER_WAR) -> BookTab.WORLD_AFTER_WAR
        else -> BookTab.DOCTRINE
    }

    fun baseUrl(book: BookTab) = when (book) {
        BookTab.HOLY_RUS -> Urls.HolyRusBase
        BookTab.WORLD_AFTER_WAR -> Urls.WorldAfterWarBase
        else -> Urls.DoctrineBase
    }

    fun siteUrl(book: BookTab) = when (book) {
        BookTab.HOLY_RUS -> Urls.HolyRusSite
        BookTab.WORLD_AFTER_WAR -> Urls.WorldAfterWarSite
        else -> Urls.DoctrineSite
    }

    fun baseName(book: BookTab) = when (book) {
        BookTab.HOLY_RUS -> DataBase.HOLY_RUS
        BookTab.WORLD_AFTER_WAR -> DataBase.WORLD_AFTER_WAR
        else -> DataBase.DOCTRINE
    }

    fun Prefix(book: BookTab) = when (book) {
        BookTab.HOLY_RUS -> Const.HOLY_RUS
        BookTab.WORLD_AFTER_WAR -> Const.WORLD_AFTER_WAR
        else -> Const.DOCTRINE
    }

    private fun getFile(book: BookTab) = Files.slash(
        when (book) {
            BookTab.HOLY_RUS -> "bookhs"
            BookTab.WORLD_AFTER_WAR -> "bookwaw"
            else -> "bookdc"
        }
    )

    private fun getLinks(book: BookTab): List<String> {
        val file = getFile(book)
        if (file.exists())
            return file.readLines()
        return listOf(siteUrl(book))
    }

    fun getList(book: BookTab): Pair<List<String>, List<String>> {
        val links = getLinks(book)
        val t = mutableListOf(App.context.getString(R.string.promo_book))
        val s = mutableListOf(links[0])
        for (i in 1..<links.size) {
            t.add(getTitle(links[i]))
            s.add(links[i])
        }
        return Pair(t, s)
    }

    private fun getTitle(link: String): String {
        val i = link.indexOf(':') + 3
        val s = link.substring(i, link.indexOf('/', i))
        return App.context.getString(R.string.site) + " $s"
    }

    fun getPromo(link: String): String {
        var s = App.context.getString(R.string.promo_book)
        val url = when {
            link.startsWith(Const.HOLY_RUS) -> getLinks(BookTab.HOLY_RUS)[0]
            link.startsWith(Const.WORLD_AFTER_WAR) -> getLinks(BookTab.WORLD_AFTER_WAR)[0]
            else -> getLinks(BookTab.DOCTRINE)[0]
        }
        val i = s.lastIndexOf(' ') + 1
        s = s.take(i) + "<a href='$url'>" + s.substring(i)
        return "<BLOCKQUOTE>$s</a>.</BLOCKQUOTE>\n"
    }

    fun loadLinks() {
        val f1 = getFile(BookTab.DOCTRINE)
        val f2 = getFile(BookTab.HOLY_RUS)
        val f3 = getFile(BookTab.WORLD_AFTER_WAR)
        var need = false
        if (!f1.exists() || !f2.exists() || !f3.exists())
            need = true
        else {
            val n = System.currentTimeMillis()
            need = n - f1.lastModified() > DateUnit.MONTH_IN_MILLS ||
                    n - f2.lastModified() > DateUnit.MONTH_IN_MILLS ||
                    n - f3.lastModified() > DateUnit.MONTH_IN_MILLS
        }
        if (need) {
            val request = Request.Builder()
            request.header(NetConst.USER_AGENT, App.context.packageName)
            request.header(NetConst.REFERER, Urls.Host)
            val client = NeoClient.createHttpClient()
            val L = "links.txt"
            downloadFile(client, request, baseUrl(BookTab.DOCTRINE) + L, f1)
            downloadFile(client, request, baseUrl(BookTab.HOLY_RUS) + L, f2)
            downloadFile(client, request, baseUrl(BookTab.WORLD_AFTER_WAR) + L, f3)
        }
    }

    private fun downloadFile(
        client: OkHttpClient,
        request: Request.Builder,
        url: String,
        file: File
    ) {
        request.url(url)
        val response = client.newCall(request.build()).execute()
        if (response.isSuccessful.not()) return
        val br = BufferedReader(response.body.charStream(), 1000)
        val bw = BufferedWriter(OutputStreamWriter(FileOutputStream(file)))
        bw.write(br.readText())
        br.close()
        bw.close()
    }
}