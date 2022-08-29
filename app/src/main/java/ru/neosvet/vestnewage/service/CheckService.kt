package ru.neosvet.vestnewage.service

import android.content.Intent
import android.os.Build
import androidx.lifecycle.LifecycleService
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.NeoList
import ru.neosvet.vestnewage.helper.SummaryHelper
import ru.neosvet.vestnewage.loader.page.PageLoader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.NetConst
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.NotificationUtils
import ru.neosvet.vestnewage.utils.UnreadUtils
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.InputStreamReader

class CheckService : LifecycleService() {
    companion object {
        @JvmStatic
        fun postCommand(start: Boolean) {
            val intent = Intent(App.context, CheckService::class.java)
            intent.putExtra(Const.START, start)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                App.context.startForegroundService(intent)
            else
                App.context.startService(intent)
        }
    }

    private val list = NeoList<Pair<String, String>>() //title and link
    private var isRun = false
    private val loader = PageLoader()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || !intent.getBooleanExtra(Const.START, false)) {
            stopForeground(true)
            return START_NOT_STICKY
        }
        if (isRun)
            return START_NOT_STICKY
        isRun = true
        initNotif()
        Thread { startLoad() }.start()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun initNotif() {
        val context = applicationContext
        val notifHelper = NotificationUtils()
        val notif = notifHelper.getNotification(
            context.getString(R.string.site_name),
            context.getString(R.string.check_new),
            NotificationUtils.CHANNEL_MUTE
        )
            .setProgress(0, 0, true)
        startForeground(NotificationUtils.NOTIF_CHECK, notif.build())
    }

    private fun startLoad() {
        try {
            if (checkSummary() && list.isNotEmpty)
                existsUpdates()
        } catch (ignored: Exception) {
        }
        loader.cancel()
        isRun = false
        postCommand(false)
    }

    private fun checkSummary(): Boolean {
        val stream = NeoClient.getStream(
            NetConst.SITE + "rss/?" + System.currentTimeMillis()
        )
        val site = if (NeoClient.isMainSite)
            NetConst.SITE.substring(
                NetConst.SITE.indexOf("/") + 2
            )
        else
            NetConst.SITE2.substring(
                NetConst.SITE2.indexOf("/") + 2
            )
        val br = BufferedReader(InputStreamReader(stream), 1000)
        var s = br.readLine()
        br.close()
        stream.close()
        var a = s.indexOf("lastBuildDate") + 14
        val secList = DateUnit.parse(s.substring(a, s.indexOf("<", a))).timeInSeconds
        val file = Lib.getFile(Const.RSS)
        var secFile: Long = 0
        if (file.exists()) secFile = DateUnit.putMills(file.lastModified()).timeInSeconds
        if (secFile > secList) { //список в загрузке не нуждается
            br.close()
            return false
        }
        val bwRSS = BufferedWriter(FileWriter(file))
        val unread = UnreadUtils()
        var d: DateUnit
        var title: String
        var link: String
        var b: Int
        val m = s.split("<item>").toTypedArray()
        for (i in 1 until m.size) {
            a = m[i].indexOf("</link")
            link = withOutTag(m[i].substring(0, a))
            if (link.contains(site)) link = link.substring(link.indexOf("info/") + 5)
            if (link.contains("#0")) link = link.replace("#0", "#2")
            b = m[i].indexOf("</title")
            title = withOutTag(m[i].substring(a + 10, b))
            bwRSS.write(title) //title
            bwRSS.write(Const.N)
            bwRSS.write(link) //link
            bwRSS.write(Const.N)
            a = m[i].indexOf("</des")
            bwRSS.write(withOutTag(m[i].substring(b + 10, a))) //des
            bwRSS.write(Const.N)
            b = m[i].indexOf("</a10")
            s = withOutTag(m[i].substring(a + 15, b))
            d = DateUnit.parse(s)
            bwRSS.write(d.timeInMills.toString() + Const.N) //time
            bwRSS.flush()
            if (unread.addLink(link, d)) {
                list.add(Pair(title, link))
                loader.download(link, false)
            }
        }
        bwRSS.close()
        loader.finish()
        unread.setBadge()
        return true
    }

    private fun withOutTag(s: String): String {
        return s.substring(s.indexOf(">") + 1)
    }

    private fun existsUpdates() {
        val summaryHelper = SummaryHelper()
        summaryHelper.updateBook()
        val several = list.first() != list.current()
        list.reset(true)
        list.forEach {
            if (summaryHelper.isNotification && !several)
                summaryHelper.showNotification()
            summaryHelper.createNotification(it.first, it.second)
            if (several)
                summaryHelper.muteNotification()
        }
        if (several)
            summaryHelper.groupNotification()
        else
            summaryHelper.singleNotification(list.first().first)
        summaryHelper.setPreferences()
        summaryHelper.showNotification()
        list.clear()
    }
}