package ru.neosvet.vestnewage.viewmodel

import android.content.Context
import android.database.Cursor
import androidx.core.content.edit
import androidx.work.Data
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.helper.MainHelper
import ru.neosvet.vestnewage.loader.SiteLoader
import ru.neosvet.vestnewage.loader.page.PageLoader
import ru.neosvet.vestnewage.loader.page.StyleLoader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.NetConst
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.PromUtils
import ru.neosvet.vestnewage.utils.WordsUtils
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.ListState
import ru.neosvet.vestnewage.viewmodel.state.MainState
import ru.neosvet.vestnewage.viewmodel.state.NeoState
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

    fun setPublicState(state: NeoState) {
        scope.launch {
            delay(3000)
            postState(state)
        }
    }

    override suspend fun doLoad() {
        Urls.update(client)
        val words = WordsUtils()
        words.update()
        val timeDiff = synchronizationTime()
        val loader = SiteLoader(client)
        val hasNew = loader.loadDevAds()
        postState(MainState.Ads(hasNew, loader.warnIndex, timeDiff))
        loadNew()
        postState(BasicState.Success)
    }

    private suspend fun loadNew() {
        val br = BufferedReader(InputStreamReader(client.getStream(Urls.DevSite + "new.txt")))
        var time: Long
        val storage = PageStorage()
        var cursor: Cursor
        val list = mutableListOf<BasicItem>()
        val loader = PageLoader(client)
        var isUpdate = false
        withContext(Dispatchers.IO) {
            var s = br.readLine()
            while (s != Const.END) {
                time = s.toLong()
                s = br.readLine() //link
                storage.open(s)
                cursor = storage.getPage(s)
                if (cursor.moveToFirst()) {
                    val iTime = cursor.getColumnIndex(Const.TIME)
                    if (time > cursor.getLong(iTime)) {
                        isUpdate = true
                        loader.download(s, true)
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
        }
        if (isUpdate) {
            val styleLoader = StyleLoader()
            styleLoader.download(false)
        }
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
            pref.edit { putInt(Const.TIMEDIFF, timeDiff) }
            val time = pref.getInt(Const.TIME, Const.TURN_OFF)
            if (time != Const.TURN_OFF) {
                val prom = PromUtils(null)
                prom.initNotif(time)
            }
        }
    }

    fun setArgument(withSplash: Boolean) {
        this.withSplash = withSplash
    }
}