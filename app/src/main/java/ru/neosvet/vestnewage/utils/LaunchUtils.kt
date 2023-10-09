package ru.neosvet.vestnewage.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.Section
import ru.neosvet.vestnewage.helper.BrowserHelper
import ru.neosvet.vestnewage.helper.MainHelper
import ru.neosvet.vestnewage.helper.SearchHelper
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.activity.MainActivity
import ru.neosvet.vestnewage.view.activity.TipActivity
import ru.neosvet.vestnewage.view.activity.TipName
import ru.neosvet.vestnewage.view.list.RequestAdapter
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileReader
import java.io.FileWriter

class LaunchUtils(context: Context) {
    companion object {
        private const val START_ID = 900
        private val FLAGS =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        private const val PREF_NAME = "main"
    }

    data class InputData(val tab: Int, val section: Section)

    private val pref: SharedPreferences
    private val settingsIntent: Intent
    private val previousVer: Int
    private var notifId = START_ID
    private var notifUtils: NotificationUtils? = null

    val isNeedLoad: Boolean
        get() = DateUnit.isLongAgo(pref.getLong(Const.TIME, 0))

    init {
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        settingsIntent = Intent(context, MainActivity::class.java)
        settingsIntent.putExtra(Const.CUR_ID, Section.SETTINGS.toString())
        previousVer = pref.getInt("ver", 0)
        if (previousVer < App.version) {
            val editor = pref.edit()
            editor.putInt("ver", App.version)
            editor.apply()
        }
    }

    fun checkAdapterNewVersion() {
        if (previousVer == 0) {
            notifUtils = NotificationUtils()
            showNotifTip(
                App.context.getString(R.string.check_out_settings),
                App.context.getString(R.string.example_periodic_check), settingsIntent
            )
            showNotifDownloadAll()
            showSummaryNotif()
            return
        }
        if (previousVer < 63) checkSearchDates()
        if (previousVer < 64) {
            refTips()
            Thread {
                checkSearchRequests()
                sortBase()
            }.start()
        }
        if (previousVer < 65) {
            val file = Files.getFileP("/cache/file")
            if (file.exists()) file.delete()
        }
        if (previousVer < 67) {
            val file = Files.getFileDB(DataBase.ADDITION)
            if (file.exists()) file.delete()
        }
        if (previousVer < 69) {
            val file = Files.getFileDB("07.23")
            if (file.exists()) file.delete()
        }
        if (previousVer < 71) {
            listOf(
                Files.getFileDB(DataBase.JOURNAL), Files.getFileDB("devads"),
                Files.getFileDB("devads-journal"), Files.getFileS("news")
            ).forEach {
                if (it.exists()) it.delete()
            }
        }
    }

    private fun refTips() {
        val name = "calendar"
        val pref = App.context.getSharedPreferences(TipActivity.TAG, Context.MODE_PRIVATE)
        val editor = pref.edit()
        var p = App.context.getSharedPreferences(name, Context.MODE_PRIVATE)
        if (!p.getBoolean(TipActivity.TAG, true))
            editor.putBoolean(TipName.CALENDAR.toString(), false)
        val f = Files.getFileP("/shared_prefs/$name.xml")
        f.delete()

        p = App.context.getSharedPreferences(MainHelper.TAG, Context.MODE_PRIVATE)
        if (!p.getBoolean(TipActivity.TAG, true))
            editor.putBoolean(TipName.MAIN_STAR.toString(), false)
        p.edit().remove(TipActivity.TAG).apply()

        p = App.context.getSharedPreferences(BrowserHelper.TAG, Context.MODE_PRIVATE)
        if (!p.getBoolean(TipActivity.TAG, true))
            editor.putBoolean(TipName.BROWSER_PANEL.toString(), false)
        p.edit().remove(TipActivity.TAG).apply()

        editor.apply()
    }

    private fun sortBase() {
        try {
            val storage = PageStorage()
            val list = mutableListOf<Pair<Int, String>>()
            var d = DateUnit.initToday()
            val max = d.timeInDays + 1
            d = DateUnit.putYearMonth(2017, 1)
            var n: Int
            while (d.timeInDays < max) {
                if (Files.getFileDB(d.my).exists()) {
                    storage.open(d.my)
                    val cursor = storage.getListAll()
                    if (cursor.moveToFirst()) {
                        val l = cursor.getColumnIndex(Const.LINK)
                        val i = cursor.getColumnIndex(DataBase.ID)
                        while (cursor.moveToNext())
                            list.add(Pair(cursor.getInt(i), cursor.getString(l)))
                    }
                    cursor.close()
                    list.sortBy { it.second }
                    var x = 900
                    var i = 0
                    while (i < list.size) {
                        val a = list[i].first
                        val b = i + 2
                        if (a == b) {
                            i++
                            continue
                        }
                        val r = list.find { p -> p.first == b }
                        if (r == null) {
                            storage.changeId(a, b)
                        } else {
                            n = list.indexOf(r)
                            if (n + 2 == a) {
                                storage.replaceId(a, b)
                                list.removeAt(n)
                            } else {
                                while (list.find { p -> p.first == x } != null)
                                    x++
                                storage.changeId(r.first, x)
                                n = list.indexOf(r)
                                list.removeAt(n)
                                list.add(n, Pair(x, r.second))
                                x++
                                storage.changeId(a, b)
                            }
                        }
                        i++
                    }
                    storage.close()
                    list.clear()
                }
                d.changeMonth(1)
            }
        } catch (ignore: Exception) {
        }
    }

    private fun checkSearchRequests() {
        val f = Files.getFileS(Const.SEARCH)
        if (!f.exists()) return
        val list = mutableListOf<String>()
        val br = BufferedReader(FileReader(f))
        br.forEachLine {
            if (!list.contains(it)) {
                list.add(it)
                if (list.size == RequestAdapter.LIMIT) return@forEachLine
            }
        }
        br.close()
        f.delete()
        val bw = BufferedWriter(FileWriter(f))
        list.forEach {
            bw.write(it + Const.N)
        }
        bw.close()
    }

    private fun checkSearchDates() {
        val pref = App.context.getSharedPreferences(SearchHelper.TAG, Context.MODE_PRIVATE)
        val a = pref.getInt(Const.START, 0)
        val b = pref.getInt(Const.END, 0)
        if (a > b) {
            val editor = pref.edit()
            editor.putInt(Const.START, b)
            editor.putInt(Const.END, a)
            editor.putBoolean(SearchHelper.INVERT, true)
            editor.apply()
        }
    }

    private fun showNotifDownloadAll() {
        Thread {
            try {
                Thread.sleep(1000)
                val intent = Intent(App.context, MainActivity::class.java)
                intent.putExtra(Const.DIALOG, true)
                showNotifTip(
                    App.context.getString(R.string.downloads_all_title),
                    App.context.getString(R.string.downloads_all_msg), intent
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun showNotifTip(title: String, msg: String, intent: Intent) {
        val piStart = PendingIntent.getActivity(App.context, 0, intent, FLAGS)
        val notifBuilder = notifUtils!!.getNotification(
            title, msg, NotificationUtils.CHANNEL_TIPS
        )
        notifBuilder.setContentIntent(piStart)
        notifBuilder.setGroup(NotificationUtils.GROUP_TIPS)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) { // on N with setFullScreenIntent don't work summary group
            val piEmpty = PendingIntent.getActivity(App.context, 0, Intent(), FLAGS)
            notifBuilder.setFullScreenIntent(piEmpty, false)
        }
        notifBuilder.setSound(null)
        notifUtils!!.notify(++notifId, notifBuilder)
    }

    private fun showSummaryNotif() {
        if (notifId - START_ID < 2) return  //notifications < 2, summary is not need
        val notifBuilder = notifUtils!!.getSummaryNotif(
            App.context.getString(R.string.tips),
            NotificationUtils.CHANNEL_TIPS
        )
        notifBuilder.setGroup(NotificationUtils.GROUP_TIPS)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) notifBuilder.setContentIntent(
            PendingIntent.getActivity(
                App.context, 0,
                settingsIntent, FLAGS
            )
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val piEmpty = PendingIntent.getActivity(App.context, 0, Intent(), FLAGS)
            notifBuilder.setFullScreenIntent(piEmpty, false)
        }
        notifUtils!!.notify(START_ID, notifBuilder)
    }

    fun reInitProm(timeDiff: Int) {
        val pref = App.context.getSharedPreferences(PromUtils.TAG, Context.MODE_PRIVATE)
        if (timeDiff != pref.getInt(Const.TIMEDIFF, 0)) {
            val editor = pref.edit()
            editor.putInt(Const.TIMEDIFF, timeDiff)
            editor.apply()
            val prom = PromUtils(null)
            prom.initNotif(timeDiff)
        }
    }

    fun openLink(intent: Intent): InputData? {
        if (intent.getBooleanExtra(Const.ADS, false))
            return InputData(2, Section.SITE)
        val data = intent.data ?: return null
        var link = data.path ?: return null // without host
        val mLink = link.substring(1).split("/")
        return when {
            link.contains(Const.RSS) -> {
                if (intent.hasExtra(DataBase.ID)) {
                    val id = intent.getIntExtra(DataBase.ID, NotificationUtils.NOTIF_SUMMARY)
                    clearSummaryNotif(id)
                }
                InputData(0, Section.SUMMARY)
            }

            link.length < 2 || link == "/index.html" ->
                InputData(1, Section.SITE)

            link == "/novosti.html" ->
                InputData(0, Section.SITE)

            link.contains("/tolkovaniya") || mLink[0] == "year.html" ->
                InputData(1, Section.BOOK)

            mLink[1].length == 13 -> { //08.02.16.html
                openReader(link.substring(1), null)
                InputData(-1, Section.MENU)
            }

            link.contains("-") -> { //https://blagayavest.info/poems/2022-09-17
                val i = link.lastIndexOf("/") + 3
                link = Const.POEMS + "/" + link.substring(i + 6, i + 8) + "." +
                        link.substring(i + 3, i + 5) + "." + link.substring(i, i + 2) + ".html"
                openReader(link, null)
                InputData(-1, Section.MENU)
            }

            link.contains("/poems") -> {
                if (mLink[1] == "year.html") InputData(0, Section.BOOK)
                else InputData(mLink[1].substring(0, 4).toInt(), Section.BOOK)
            }

            mLink[1].contains("date") -> { //http://blagayavest.info/poems/?date=11-3-2017
                val s = mLink[1].substring(5)
                val m = s.substring(s.indexOf("-") + 1, s.lastIndexOf("-"))
                link = (link.substring(1) + s.substring(0, s.indexOf("-"))
                        + "." + (if (m.length == 1) "0" else "") + m
                        + "." + s.substring(s.lastIndexOf("-") + 3) + Const.HTML)
                openReader(link, null)
                InputData(-1, Section.MENU)
            }

            else -> null
        }
    }

    fun updateTime() {
        val editor = pref.edit()
        editor.putLong(Const.TIME, System.currentTimeMillis())
        editor.apply()
    }

    fun clearSummaryNotif(id: Int) {
        if (id != 0) {
            val helper = NotificationUtils()
            var i = NotificationUtils.NOTIF_SUMMARY
            while (i <= id) {
                helper.cancel(i)
                i++
            }
        }
    }
}