package ru.neosvet.vestnewage.service

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.LifecycleService
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.NeoList
import ru.neosvet.vestnewage.helper.SummaryHelper
import ru.neosvet.vestnewage.loader.AdditionLoader
import ru.neosvet.vestnewage.loader.SummaryLoader
import ru.neosvet.vestnewage.loader.page.PageLoader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Files
import ru.neosvet.vestnewage.utils.NotificationUtils
import ru.neosvet.vestnewage.utils.UnreadUtils
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileReader
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
    private val client = NeoClient()
    private val loader = PageLoader(client)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || !intent.getBooleanExtra(Const.START, false)) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M)
                stopForeground(true)
            else stopForeground(STOP_FOREGROUND_REMOVE)
            return START_NOT_STICKY
        }
        if (isRun) return START_NOT_STICKY
        isRun = true
        initNotif()
        Thread { startLoad() }.start()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun initNotif() {
        val context = applicationContext
        val utils = NotificationUtils()
        val notif = utils.getNotification(
            context.getString(R.string.app_name),
            context.getString(R.string.check_new),
            NotificationUtils.CHANNEL_MUTE
        )
            .setProgress(0, 0, true)
        startForeground(NotificationUtils.NOTIF_CHECK, notif.build())
    }

    private fun startLoad() {
        try {
            Urls.restore()
            checkSummary()
            val pref = getSharedPreferences(SummaryHelper.TAG, Context.MODE_PRIVATE)
            if (pref.getBoolean(Const.MODE, true)) checkAddition()
            if (pref.getBoolean(Const.DOCTRINE, false)) checkDoctrine()
            if (pref.getBoolean(Const.PLACE, false)) checkAcademy()
            if (list.isNotEmpty) existsUpdates()
        } catch (ignored: Exception) {
        }
        loader.cancel()
        isRun = false
        postCommand(false)
    }

    private fun checkAcademy() {
        val file = Files.file(Files.ACADEMY)
        if (file.exists() && !DateUnit.isVeryLongAgo(file.lastModified())) return
        val stream = client.getStream(Urls.ACADEMY + "/Press/News/")
        var br = BufferedReader(InputStreamReader(stream), 1000)
        var s = br.readLine()
        while (!s.contains("sm-blog-list-date"))
            s = br.readLine()
        br.close()
        val i = s.indexOf("<span>") + 6
        s = s.substring(i, s.indexOf("<", i))
        val timeList = DateUnit.parse(s).timeInMills
        br = BufferedReader(FileReader(file))
        br.readLine() //title
        br.readLine() //link
        br.readLine() //des always empty
        val timeFile = br.readLine().toLong()
        br.close()
        if (timeFile == timeList) {
            file.setLastModified(System.currentTimeMillis())
            return
        }
        val loader = SummaryLoader(client)
        loader.loadAcademy()
        list.add(Pair(getString(R.string.new_in_academy), Files.RSS + "3"))
    }

    private fun checkDoctrine() {
        val file = Files.file(Files.DOCTRINE)
        val timeFile = if (file.exists()) file.lastModified()
        else 0L
        if (timeFile > 0L && !DateUnit.isVeryLongAgo(timeFile)) return
        val stream = client.getStream(Urls.DOCTRINE + "feed/")
        val br = BufferedReader(InputStreamReader(stream), 1000)
        var s = br.readLine()
        while (!s.contains("pubDate"))
            s = br.readLine()
        val i = s.indexOf("Date>") + 5
        br.close()
        val timeList = DateUnit.parse(s.substring(i, s.indexOf("<", i))).timeInMills
        if (timeFile == timeList) {
            file.setLastModified(System.currentTimeMillis())
            return
        }
        val loader = SummaryLoader(client)
        loader.loadDoctrine()
        list.add(Pair(getString(R.string.new_in_doctrine), Files.RSS + "2"))
    }

    private fun checkAddition() {
        val loader = AdditionLoader(client)
        if (loader.checkUpdate())
            list.add(Pair(getString(R.string.new_in_additionally), Files.RSS + "1"))
    }

    private fun checkSummary() {
        val stream = client.getStream(Urls.RSS)
        val br = BufferedReader(InputStreamReader(stream), 1000)
        var s = br.readLine()
        if (Urls.isSiteCom) {
            while (!s.contains("pubDate"))
                s = br.readLine()
        }
        var a = s.indexOf("Date>") + 5
        val timeList = DateUnit.parse(s.substring(a, s.indexOf("<", a))).timeInMills
        val file = Files.file(Files.RSS)
        val timeFile = if (file.exists()) file.lastModified()
        else 0L
        if (timeFile > timeList) {
            br.close()
            return
        }
        val m = (if (Urls.isSiteCom) br.readText() else s).split("<item>")
        br.close()
        val bw = BufferedWriter(FileWriter(file))
        val host = Urls.Host
        val unread = UnreadUtils()
        var d: DateUnit
        var title: String
        var link: String
        var b: Int

        for (i in 1 until m.size) {
            a = m[i].indexOf("<link") + 6
            b = m[i].indexOf("</", a)
            link = m[i].substring(a, b)
            if (link.contains(host))
                link = link.substring(link.indexOf(host) + host.length + 1)
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
    }

    private fun withOutTag(s: String): String {
        return s.substring(s.indexOf(">") + 1)
    }

    private fun existsUpdates() {
        val helper = SummaryHelper()
        helper.preparingNotification()
        val several = list.size > 1
        if (several || list.first().second != Files.RSS)
            helper.updateBook()
        list.reset(true)
        list.forEach {
            helper.createNotification(it.first, it.second)
            if (several) {
                helper.muteNotification()
                helper.showNotification()
            }
        }
        if (several) helper.groupNotification()
        else helper.singleNotification(list.current().first)
        helper.setPreferences()
        helper.showNotification()
        list.clear()
    }
}