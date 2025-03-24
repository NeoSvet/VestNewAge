package ru.neosvet.vestnewage.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.Section
import ru.neosvet.vestnewage.helper.DateHelper
import ru.neosvet.vestnewage.helper.SummaryHelper
import ru.neosvet.vestnewage.storage.DataBase
import ru.neosvet.vestnewage.storage.MarkersStorage
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.activity.MainActivity
import java.io.File

class LaunchUtils(context: Context) {
    companion object {
        const val DEV_TAB = "/.dev"
        private const val START_ID = 900
        private val FLAGS =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        private const val PREF_NAME = "main"
    }

    data class InputData(val tab: Int, val section: Section)

    private val pref: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val settingsIntent: Intent = Intent(context, MainActivity::class.java)
    private val previousVer: Int
    private var notifId = START_ID
    private var notifUtils: NotificationUtils? = null

    val isNeedLoad: Boolean
        get() = DateUnit.isLongAgo(pref.getLong(Const.TIME, 0))

    init {
        settingsIntent.putExtra(Const.CUR_ID, Section.SETTINGS.toString())
        previousVer = pref.getInt("ver", 0)
        if (previousVer < App.version) {
            pref.edit {
                putInt("ver", App.version)
            }
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
        if (previousVer < 74 && DateHelper.isLoadedOtkr())
            Thread {
                removePrintInLinks()
            }.start()
    }

    private fun removePrintInLinks() {
        val d = DateUnit.putYearMonth(2004, 8)
        val storage = PageStorage()
        val markers = MarkersStorage()
        val folder = App.context.filesDir.parent?.plus("/databases/")
        while (d.timeInDays < DateHelper.MIN_DAYS_NEW_BOOK) {
            if (File(folder + d.my).exists()) {
                storage.open(d.my)
                storage.getLinksList().forEach {
                    if (it.startsWith("print/")) {
                        val p = it.substring(6)
                        storage.changeLink(it, p)
                        markers.changeLink(it, p)
                    }
                }
                storage.close()
            }
            d.changeMonth(1)
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

    fun openLink(intent: Intent): InputData? {
        if (intent.getBooleanExtra(Const.ADS, false))
            return InputData(2, Section.SITE)
        val data = intent.data ?: return null
        var link = data.path ?: return null // without host
        val mLink = link.substring(1).split("/")
        return when {
            data.host == "chenneling.info" -> {
                if (link.length < 2) {
                    val d = DateUnit.initToday()
                    link = "/poems/$d.html"
                }
                openReader(link.substring(1), null)
                InputData(-1, Section.MENU)
            }

            link.indexOf(SummaryHelper.TAG) == 0 -> {
                if (intent.hasExtra(DataBase.ID)) {
                    val id = intent.getIntExtra(DataBase.ID, NotificationUtils.NOTIF_SUMMARY)
                    clearSummaryNotif(id)
                }
                val tab = link.substring(SummaryHelper.TAG.length).toInt()
                InputData(tab, Section.SUMMARY)
            }

            link.contains(Files.RSS) ->
                InputData(0, Section.SUMMARY)

            link.length < 2 || link == "/index.html" ->
                InputData(1, Section.SITE)

            link == "/novosti.html" ->
                InputData(0, Section.SITE)

            link.indexOf(DEV_TAB) == 1 ->
                InputData(2, Section.SITE)

            link.indexOf(DataBase.JOURNAL) == 1 ->
                InputData(0, Section.JOURNAL)

            link.contains("/tolkovaniya") || mLink[0] == "year.html" ->
                InputData(1, Section.BOOK)

            mLink.size == 1 || mLink[1].length == 13 -> { //08.02.16.html
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

            link.isPoem -> {
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
        pref.edit {
            putLong(Const.TIME, System.currentTimeMillis())
        }
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