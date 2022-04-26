package ru.neosvet.vestnewage.model


import android.content.Context
import android.database.Cursor
import androidx.work.Data
import okhttp3.Request
import ru.neosvet.utils.Const
import ru.neosvet.utils.NeoClient
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.helpers.DateHelper
import ru.neosvet.vestnewage.helpers.DevadsHelper
import ru.neosvet.vestnewage.service.LoaderService
import ru.neosvet.vestnewage.list.ListItem
import ru.neosvet.vestnewage.model.basic.AdsState
import ru.neosvet.vestnewage.model.basic.NeoViewModel
import ru.neosvet.vestnewage.model.basic.SuccessList
import ru.neosvet.vestnewage.storage.PageStorage
import java.io.BufferedReader
import java.io.InputStreamReader


class MainModel : NeoViewModel() {
    private lateinit var updated_page: String

    fun init(context: Context) {
        updated_page = context.getString(R.string.updated_page)
    }

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, "slash")
        .build()

    override suspend fun doLoad() {
        val timeDiff = synchronizationTime()
        val ads = DevadsHelper(App.context)
        ads.loadAds()
        ads.close()
        mstate.postValue(AdsState(ads.hasNew(), ads.warnIndex, timeDiff))
        loadNew()
    }

    override fun onDestroy() {
    }

    private fun loadNew() {
        var s = "http://neosvet.ucoz.ru/vna/new.txt"
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
                    LoaderService.postCommand(
                        LoaderService.DOWNLOAD_PAGE, s)
                    val iTitle = cursor.getColumnIndex(Const.TITLE)
                    list.add(ListItem(cursor.getString(iTitle), s).apply {
                        des=updated_page
                    })
                }
            }
            s = br.readLine()
            storage.close()
        }
        br.close()
        if (list.isNotEmpty())
            mstate.postValue(SuccessList(list))
    }

    private fun synchronizationTime(): Int {
        val builderRequest = Request.Builder()
        builderRequest.url(NeoClient.SITE)
        builderRequest.header(NeoClient.USER_AGENT, App.context.packageName)
        val client = NeoClient.createHttpClient()
        val response = client.newCall(builderRequest.build()).execute()
        val s = response.headers.value(1)
        val timeServer = DateHelper.parse(s).timeInSeconds
        response.close()
        val timeDevice = DateHelper.initNow().timeInSeconds
        return (timeDevice - timeServer).toInt()
    }
}