package ru.neosvet.vestnewage.helper

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.NotificationUtils
import ru.neosvet.vestnewage.view.activity.MainActivity
import ru.neosvet.vestnewage.view.dialog.SetNotifDialog
import java.io.BufferedReader
import java.io.FileReader

class SummaryHelper {

    companion object {
        const val TAG = "Summary"
        private const val TEN_MIN_IN_MILLS = 600000
        private val FLAGS = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE

        fun postpone(des: String, link: String) {
            Toast.makeText(
                App.context,
                App.context.getString(R.string.postpone_alert),
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent(App.context, Rec::class.java)
            intent.putExtra(Const.DESCTRIPTION, des)
            intent.putExtra(Const.LINK, link)
            val piPostpone = PendingIntent.getBroadcast(App.context, 3, intent, FLAGS)
            NotificationUtils.setAlarm(piPostpone, TEN_MIN_IN_MILLS + System.currentTimeMillis())
        }
    }

    private val notifUtils = NotificationUtils()
    private val intent = Intent(App.context, MainActivity::class.java)
    private val piEmpty = PendingIntent.getActivity(App.context, 0, Intent(), FLAGS)
    private var notifId = NotificationUtils.NOTIF_SUMMARY + 1
    private var notifBuilder: NotificationCompat.Builder? = null

    fun updateBook() {
        val file = Lib.getFile(Const.RSS)
        val br = BufferedReader(FileReader(file))
        val storage = PageStorage()
        br.forEachLine {
            val link = br.readLine()
            br.readLine() //des
            br.readLine() //time
            storage.open(link, true)
            if (!storage.existsPage(link)) {
                val row = ContentValues()
                row.put(Const.TITLE, it)
                row.put(Const.LINK, link)
                storage.insertTitle(row)
                storage.updateTime()
            }
        }
        br.close()
        storage.close()
    }

    fun createNotification(text: String, link: String) {
        val url = if (!link.contains("://")) Urls.Site + link else link
        intent.data = Uri.parse(url)
        val piSummary = PendingIntent.getActivity(App.context, 0, intent, FLAGS)
        val piPostpone = notifUtils.getPostponeSummaryNotif(notifId, text, url)
        val title = if (link == Const.RSS) App.context.getString(R.string.app_name)
        else App.context.getString(R.string.site_name)
        notifBuilder = notifUtils.getNotification(
            title, text,
            NotificationUtils.CHANNEL_SUMMARY
        ).also {
            it.setContentIntent(piSummary)
                .setGroup(NotificationUtils.GROUP_SUMMARY)
                .addAction(0, App.context.getString(R.string.postpone), piPostpone)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
                it.setFullScreenIntent(piEmpty, true)
        }
    }

    fun muteNotification() {
        notifBuilder?.setChannelId(NotificationUtils.CHANNEL_MUTE)
    }

    fun showNotification() = notifBuilder?.let {
        notifUtils.notify(notifId, it)
        notifId++
    }

    fun groupNotification() {
        intent.data = Uri.parse(Urls.Site + Const.RSS)
        intent.putExtra(DataBase.ID, notifId)
        val piSummary = PendingIntent.getActivity(App.context, 0, intent, FLAGS)
        notifBuilder = notifUtils.getSummaryNotif(
            App.context.getString(R.string.appeared_new_some),
            NotificationUtils.CHANNEL_SUMMARY
        ).also {
            it.setContentIntent(piSummary)
                .setGroup(NotificationUtils.GROUP_SUMMARY)
                .setGroupSummary(true)
            it.setFullScreenIntent(piEmpty, true)
        }
    }

    fun singleNotification(text: String) = notifBuilder?.run {
        setContentText(text)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            setFullScreenIntent(piEmpty, true)
    }

    fun setPreferences() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return
        val pref = App.context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        val sound = pref.getBoolean(SetNotifDialog.SOUND, false)
        val vibration = pref.getBoolean(SetNotifDialog.VIBR, true)
        notifBuilder?.run {
            setLights(Color.GREEN, DateUnit.SEC_IN_MILLS, DateUnit.SEC_IN_MILLS)
            if (sound) {
                val uri = pref.getString(SetNotifDialog.URI, null)
                if (uri == null)
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                else setSound(Uri.parse(uri))
            }
            if (vibration) setVibrate(longArrayOf(500, 1500))
        }
    }

    class Rec : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val des = intent.getStringExtra(Const.DESCTRIPTION)
            val link = intent.getStringExtra(Const.LINK)
            if (des == null || link == null) return
            val summaryHelper = SummaryHelper()
            summaryHelper.createNotification(des, link)
            summaryHelper.showNotification()
        }
    }
}