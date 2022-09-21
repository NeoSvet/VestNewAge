package ru.neosvet.vestnewage.service

import android.content.Intent
import android.os.Build
import androidx.lifecycle.LifecycleService
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.NeoList
import ru.neosvet.vestnewage.helper.SummaryHelper
import ru.neosvet.vestnewage.loader.SummaryLoader
import ru.neosvet.vestnewage.loader.page.PageLoader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.NetConst
import ru.neosvet.vestnewage.storage.AdditionStorage
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
    private val client = NeoClient(NeoClient.Type.CHECK)
    private val loader = PageLoader(client)

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
            if ((checkSummary() || checkAddition()) && list.isNotEmpty)
                existsUpdates()
        } catch (ignored: Exception) {
        }
        loader.cancel()
        isRun = false
        postCommand(false)
    }

    private fun checkAddition(): Boolean {
        val storage = AdditionStorage()
        storage.open()
        storage.findMax()
        val loader = SummaryLoader(client)
        val max = loader.loadMax()
        if (max > storage.max) {
            loader.loadAddition(storage, storage.max)
            storage.close()
            list.add(Pair(getString(R.string.new_in_additionally), Const.RSS))
            return true
        }
        storage.close()
        return false
    }

    private fun checkSummary(): Boolean {
        val stream = client.getStream(NetConst.SITE + "rss/?" + System.currentTimeMillis())
        val br = BufferedReader(InputStreamReader(stream), 1000)
        var s = br.readLine()
        if (NeoClient.isSiteCom) {
            while (!s.contains("pubDate"))
                s = br.readLine()
        }
        var a = s.indexOf("Date>") + 5
        val secList = DateUnit.parse(s.substring(a, s.indexOf("<", a))).timeInSeconds
        val file = Lib.getFile(Const.RSS)
        val secFile = if (file.exists())
            DateUnit.putMills(file.lastModified()).timeInSeconds
        else 0L
        if (secFile > secList) { //список в загрузке не нуждается
            br.close()
            return false
        }
        val m = (if (NeoClient.isSiteCom) {
            val sb = StringBuilder()
            var line: String? = br.readLine()
            while (line != null) {
                sb.append(line)
                line = br.readLine()
            }
            sb.toString()
        } else s).split("<item>")
        br.close()
        val bw = BufferedWriter(FileWriter(file))
        val site = NeoClient.getSite()
        val unread = UnreadUtils()
        var d: DateUnit
        var title: String
        var link: String
        var b: Int

        for (i in 1 until m.size) {
            a = m[i].indexOf("<link") + 6
            b = m[i].indexOf("</", a)
            link = m[i].substring(a, b)
            if (link.contains(site))
                link = link.substring(link.indexOf(site) + site.length + 1)
            if (link.contains("#0")) link = link.replace("#0", "#2")

            a = m[i].indexOf("<title") + 7
            b = m[i].indexOf("</", a)
            title = m[i].substring(a, b)
            bw.write(title) //title
            bw.write(Const.N)
            bw.write(link) //link
            bw.write(Const.N)

            a = m[i].indexOf("<des") + 13
            b = m[i].indexOf("</", a)
            bw.write(withOutTag(m[i].substring(a, b))) //des
            bw.write(Const.N)

            a = m[i].indexOf("updated>")
            if (a == -1)
                a = m[i].indexOf("pubDate>")
            b = m[i].indexOf("</", a)
            d = DateUnit.parse(m[i].substring(a + 8, b))
            bw.write(d.timeInMills.toString() + Const.N) //time
            bw.flush()

            if (unread.addLink(link, d)) {
                list.add(Pair(title, link))
                loader.download(link, false)
            }
        }
        bw.close()
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