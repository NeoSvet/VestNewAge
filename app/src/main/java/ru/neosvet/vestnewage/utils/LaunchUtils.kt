package ru.neosvet.vestnewage.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.Section
import ru.neosvet.vestnewage.helper.*
import ru.neosvet.vestnewage.storage.AdsStorage
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.activity.MainActivity
import ru.neosvet.vestnewage.view.activity.TipActivity
import ru.neosvet.vestnewage.view.activity.TipName
import ru.neosvet.vestnewage.viewmodel.SiteToiler
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileReader
import java.io.FileWriter

class LaunchUtils {
    companion object {
        private const val START_ID = 900
        private val FLAGS =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        private const val PREF_NAME = "main"
        private var ver = -1
    }

    private val pref = App.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private var notifId = START_ID
    private var notifHelper: NotificationUtils? = null
    val intent = Intent()

    val isNeedLoad: Boolean
        get() = DateUnit.isLongAgo(pref.getLong(Const.TIME, 0))
    private val settingsIntent: Intent
        get() {
            val intent = Intent(App.context, MainActivity::class.java)
            intent.putExtra(Const.CUR_ID, Section.SETTINGS.toString())
            return intent
        }
    private val previousVer: Int
        get() {
            val prev = pref.getInt("ver", 0)
            try {
                val cur = App.context.packageManager.getPackageInfo(
                    App.context.packageName,
                    0
                ).versionCode
                if (prev < cur) {
                    val editor = pref.edit()
                    editor.putInt("ver", cur)
                    editor.apply()
                    reInitProm()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return prev
        }

    fun checkAdapterNewVersion() {
        if (ver == -1) ver = previousVer
        if (ver == 0) {
            notifHelper = NotificationUtils()
            showNotifTip(
                App.context.getString(R.string.check_out_settings),
                App.context.getString(R.string.example_periodic_check), settingsIntent
            )
            showNotifDownloadAll()
            showSummaryNotif()
            return
        }
        if (ver < 60) {
            resetFirst()
            renamePrefs()
            renameBookPref()
            deleteBrowserFiles()
        }
        if (ver < 63) checkSearchDates()
        if (ver in 45..46) {
            val storage = AdsStorage()
            storage.delete()
            storage.close()
        }
        if (ver < 64) {
            refTips()
            Thread {
                checkSearchRequests()
                sortBase()
            }.start()
        }
        if (ver < 65) {
            val file = Lib.getFileP("/cache/file")
            if (file.exists()) file.delete()
        }
    }

    private fun refTips() {
        val name = "calendar"
        val pref = App.context.getSharedPreferences(TipActivity.TAG, Context.MODE_PRIVATE)
        val editor = pref.edit()
        var p = App.context.getSharedPreferences(name, Context.MODE_PRIVATE)
        if (!p.getBoolean(TipActivity.TAG, true))
            editor.putBoolean(TipName.CALENDAR.toString(), false)
        val f = Lib.getFileP("/shared_prefs/$name.xml")
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
                if (Lib.getFileDB(d.my).exists()) {
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
        val f = Lib.getFileS(Const.SEARCH)
        if (!f.exists()) return
        val list = mutableListOf<String>()
        val br = BufferedReader(FileReader(f))
        var s: String? = br.readLine()
        while (s != null && list.size < SearchHelper.REQUESTS_LIMIT) {
            if (!list.contains(s))
                list.add(s)
            s = br.readLine()
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

    private fun resetFirst() {
        val pref = App.context.getSharedPreferences(MainHelper.TAG, Context.MODE_PRIVATE)
        val editor = pref.edit()
        editor.putBoolean(Const.FIRST, true)
        editor.apply()
    }

    private fun renameBookPref() {
        val pref = App.context.getSharedPreferences(BookHelper.TAG, Context.MODE_PRIVATE)
        val editor = pref.edit()
        var i = pref.getInt("kat", 0)
        editor.remove("kat")
        editor.putInt(Const.POEMS, i)
        i = pref.getInt("pos", 0)
        editor.remove("pos")
        editor.putInt(Const.EPISTLES, i)
        editor.apply()
    }

    private fun deleteBrowserFiles() {
        var f = Lib.getFile(Const.DARK)
        if (f.exists()) f.delete()
        f = Lib.getFile(Const.LIGHT)
        if (f.exists()) f.delete()
        f = Lib.getFile("/style/style.css")
        if (f.exists()) f.delete()
        f = Lib.getFile("/page.html")
        if (f.exists()) f.delete()
    }

    private fun renamePrefs() {
        val p = "/shared_prefs/"
        val x = ".xml"
        var f = Lib.getFileP(p + "PromHelper.xml")
        if (f.exists()) f.delete()
        f = Lib.getFileP(p + "MainActivity.xml")
        if (f.exists()) f.renameTo(Lib.getFileP(p + MainHelper.TAG + x))
        f = Lib.getFileP(p + "BrowserActivity.xml")
        if (f.exists()) f.renameTo(Lib.getFileP(p + BrowserHelper.TAG + x))
        f = Lib.getFileP(p + "CabmainFragment.xml")
        if (f.exists()) f.renameTo(Lib.getFileP(p + CabinetHelper.TAG + x))
        f = Lib.getFileP(p + "BookFragment.xml")
        if (f.exists()) f.renameTo(Lib.getFileP(p + BookHelper.TAG + x))
        f = Lib.getFileP(p + "SearchFragment.xml")
        if (f.exists()) f.renameTo(Lib.getFileP(p + SearchHelper.TAG + x))
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
        val notifBuilder = notifHelper!!.getNotification(
            title, msg, NotificationUtils.CHANNEL_TIPS
        )
        notifBuilder.setContentIntent(piStart)
        notifBuilder.setGroup(NotificationUtils.GROUP_TIPS)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) { // on N with setFullScreenIntent don't work summary group
            val piEmpty = PendingIntent.getActivity(App.context, 0, Intent(), FLAGS)
            notifBuilder.setFullScreenIntent(piEmpty, false)
        }
        notifBuilder.setSound(null)
        notifHelper!!.notify(++notifId, notifBuilder)
    }

    private fun showSummaryNotif() {
        if (notifId - START_ID < 2) return  //notifications < 2, summary is not need
        val notifBuilder = notifHelper!!.getSummaryNotif(
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
        notifHelper!!.notify(START_ID, notifBuilder)
    }

    private fun reInitProm() {
        val pref = App.context.getSharedPreferences(PromUtils.TAG, Context.MODE_PRIVATE)
        val prom = PromUtils(null)
        prom.initNotif(pref.getInt(Const.TIME, Const.TURN_OFF))
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

    fun openLink(intent: Intent): Boolean {
        if (intent.getBooleanExtra(Const.ADS, false)) {
            intent.putExtra(Const.CUR_ID, Section.SITE.toString())
            intent.putExtra(Const.TAB, 2)
            return true
        }
        val data = intent.data ?: return false
        var link = data.path ?: return false
        if (link.contains(Const.RSS)) {
            intent.putExtra(Const.CUR_ID, Section.SUMMARY.toString())
            if (intent.hasExtra(DataBase.ID)) intent.putExtra(
                DataBase.ID, intent.getIntExtra(
                    DataBase.ID,
                    NotificationUtils.NOTIF_SUMMARY
                )
            )
        } else if (link.length < 2 || link == "/index.html") {
            intent.putExtra(Const.CUR_ID, Section.SITE.toString())
            intent.putExtra(Const.TAB, 0)
        } else if (link == SiteToiler.NOVOSTI) {
            intent.putExtra(Const.CUR_ID, Section.SITE.toString())
            intent.putExtra(Const.TAB, 1)
        } else if (link.contains(Const.HTML)) {
            openReader(link.substring(1), null)
        } else if (data.query != null && data.query!!.contains("date")) { //http://blagayavest.info/poems/?date=11-3-2017
            val s = data.query!!.substring(5)
            val m = s.substring(s.indexOf("-") + 1, s.lastIndexOf("-"))
            link = (link.substring(1) + s.substring(0, s.indexOf("-"))
                    + "." + (if (m.length == 1) "0" else "") + m
                    + "." + s.substring(s.lastIndexOf("-") + 3) + Const.HTML)
            openReader(link, null)
        } else if (link.contains("/poems")) {
            intent.putExtra(Const.CUR_ID, Section.BOOK.toString())
            intent.putExtra(Const.TAB, 0)
        } else if (link.contains("/tolkovaniya") || link.contains("/2016")) {
            intent.putExtra(Const.CUR_ID, Section.BOOK.toString())
            intent.putExtra(Const.TAB, 1)
        }
        return true
    }

    fun updateTime() {
        val editor = pref.edit()
        editor.putLong(Const.TIME, System.currentTimeMillis())
        editor.apply()
    }
}