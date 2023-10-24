package ru.neosvet.vestnewage.viewmodel

import android.content.Context
import android.database.Cursor
import androidx.work.Data
import okhttp3.Request
import okhttp3.internal.http.promisesBody
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.NeoException
import ru.neosvet.vestnewage.helper.MainHelper
import ru.neosvet.vestnewage.loader.SiteLoader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.NetConst
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.service.LoaderService
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.PromUtils
import ru.neosvet.vestnewage.utils.WordsUtils
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.ListState
import ru.neosvet.vestnewage.viewmodel.state.MainState
import java.io.BufferedReader
import java.io.InputStreamReader

class MainToiler : NeoToiler() {
    private lateinit var updatedPage: String
    private var client = NeoClient()
    private var withSplash = true

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, MainHelper.TAG)
        .build()

    override fun init(context: Context) {
        updatedPage = context.getString(R.string.updated_page)
    }

    override suspend fun defaultState() {
        postState(MainState.FirstRun(withSplash))
    }

    override suspend fun doLoad() {
        Urls.update(client)
        if (Urls.isSiteCom) loadQuoteCom()
        else loadQuote()
        val timeDiff = synchronizationTime()
        val loader = SiteLoader(client)
        val hasNew = loader.loadDevAds()
        postState(MainState.Ads(hasNew, loader.warnIndex, timeDiff))
        loadNew()
        postState(BasicState.Success)
    }

    private fun loadQuoteCom() {
        val request: Request = Request.Builder()
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
        WordsUtils.saveGodWords(s)
    }

    private fun loadQuote() {
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
        WordsUtils.saveGodWords(s)
    }

    private suspend fun loadNew() {
        val br = BufferedReader(InputStreamReader(client.getStream(Urls.DevSite + "new.txt")))
        var time: Long
        val storage = PageStorage()
        var cursor: Cursor
        var s = br.readLine()
        val list = mutableListOf<BasicItem>()
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
                    list.add(BasicItem(cursor.getString(iTitle), s).apply {
                        des = updatedPage
                    })
                }
            }
            s = br.readLine()
            storage.close()
        }
        br.close()
        if (list.isNotEmpty())
            postState(ListState.Primary(list = list))
    }

    private fun synchronizationTime(): Int {
        val builderRequest = Request.Builder()
        builderRequest.url(Urls.Site)
        builderRequest.header(NetConst.USER_AGENT, App.context.packageName)
        val client = NeoClient.createHttpClient()
        val response = client.newCall(builderRequest.build()).execute()
        val s = response.headers.value(1)
        val timeServer = DateUnit.parse(s).timeInSeconds
        response.close()
        val timeDevice = DateUnit.initNow().timeInSeconds
        val diff = (timeDevice - timeServer).toInt()
        reInitProm(diff)
        return diff
    }

    private fun reInitProm(timeDiff: Int) {
        val pref = App.context.getSharedPreferences(PromUtils.TAG, Context.MODE_PRIVATE)
        if (timeDiff != pref.getInt(Const.TIMEDIFF, 0)) {
            val editor = pref.edit()
            editor.putInt(Const.TIMEDIFF, timeDiff)
            editor.apply()
            if (pref.getInt(Const.TIME, Const.TURN_OFF) != Const.TURN_OFF) {
                val prom = PromUtils(null)
                prom.initNotif(timeDiff)
            }
        }
    }

    fun setArgument(withSplash: Boolean) {
        this.withSplash = withSplash
    }
}