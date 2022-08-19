package ru.neosvet.vestnewage.viewmodel


import android.content.Context
import android.database.Cursor
import androidx.work.Data
import okhttp3.Request
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.helper.MainHelper
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.NetConst
import ru.neosvet.vestnewage.service.LoaderService
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.AdsUtils
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.WordsUtils
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import java.io.BufferedReader
import java.io.InputStreamReader


class MainToiler : NeoToiler() {
    private lateinit var updatedPage: String

    fun init(context: Context) {
        updatedPage = context.getString(R.string.updated_page)
    }

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, MainHelper.TAG)
        .build()

    override suspend fun doLoad() {
        loadQuote()
        val timeDiff = synchronizationTime()
        val ads = AdsUtils(App.context)
        ads.loadAds()
        ads.close()
        postState(NeoState.Ads(ads.hasNew(), ads.warnIndex, timeDiff))
        loadNew()
    }

    private suspend fun loadQuote() {
        var s = NetConst.SITE + "AjaxData/Calendar"
        val br = BufferedReader(InputStreamReader(NeoClient.getStream(s)))
        s = br.readLine()
        br.close()
        var i = s.indexOf("quoteBlock")
        i = s.indexOf("class", i)
        s = s.substring(i, s.indexOf("\\u003C/p", i))
        s = s.replace("\\u0022", "\"").replace("\\u0020", " ").replace("\\u003Cbr\\u003E", " ")
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
        WordsUtils.saveGodWords(s)
    }

    private suspend fun loadNew() {
        var s = NetConst.WEB_PAGE + "new.txt"
        val br = BufferedReader(InputStreamReader(NeoClient.getStream(s)))
        var time: Long
        val storage = PageStorage()
        var cursor: Cursor
        s = br.readLine()
        val list = mutableListOf<ListItem>()
        while (s != Const.END) {
            time = s.toLong()
            s = br.readLine() //link
            storage.open(s)
            cursor = storage.getPage(s)
            if (cursor.moveToFirst()) {
                val iTime = cursor.getColumnIndex(Const.TIME)
                if (time > cursor.getLong(iTime)) {
                    LoaderService.loadPage(s)
                    val iTitle = cursor.getColumnIndex(Const.TITLE)
                    list.add(ListItem(cursor.getString(iTitle), s).apply {
                        des = updatedPage
                    })
                }
            }
            s = br.readLine()
            storage.close()
        }
        br.close()
        if (list.isNotEmpty())
            postState(NeoState.ListValue(list))
    }

    private suspend fun synchronizationTime(): Int {
        val builderRequest = Request.Builder()
        builderRequest.url(NetConst.SITE)
        builderRequest.header(NetConst.USER_AGENT, App.context.packageName)
        val client = NeoClient.createHttpClient()
        val response = client.newCall(builderRequest.build()).execute()
        val s = response.headers.value(1)
        val timeServer = DateUnit.parse(s).timeInSeconds
        response.close()
        val timeDevice = DateUnit.initNow().timeInSeconds
        return (timeDevice - timeServer).toInt()
    }
}