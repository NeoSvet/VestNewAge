package ru.neosvet.vestnewage.utils

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.helper.SummaryHelper
import ru.neosvet.vestnewage.storage.DataBase

//Helper class to manage notification channels, and create notifications.
class NotificationUtils : ContextWrapper(App.context) {
    companion object {
        const val NOTIF_SUMMARY = 111
        private const val ID_SUMMARY_POSTPONE = 3 //ID_SUMMARY = 2,
        const val NOTIF_PROM = 222
        const val ID_ACCEPT = 1
        const val NOTIF_CHECK = 333 //, NOTIF_SLASH = 444;
        const val CHANNEL_SUMMARY = "summary"
        const val CHANNEL_MUTE = "mute"
        const val CHANNEL_PROM = "prom"
        const val CHANNEL_TIPS = "tips"
        const val MODE = "mode"
        const val GROUP_SUMMARY = "group_summary"
        const val GROUP_TIPS = "group_tips"

        val FLAGS = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ->
                PendingIntent.FLAG_CANCEL_CURRENT

            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE

            else -> PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        }

        fun setAlarm(pi: PendingIntent, time: Long) {
            val am = App.context.getSystemService(ALARM_SERVICE) as AlarmManager
            am.cancel(pi)
            if (time == Const.TURN_OFF.toLong()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                Toast.makeText(
                    App.context,
                    App.context.getString(R.string.not_allowed_alarm),
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        }
    }

    private val manager: NotificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }
    private var notifList: MutableList<String>? = null

    //Registers notification channels, which can be used later by individual notifications.
    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (manager.notificationChannels.size == 0) // no channels
                createChannels()
        }
    }

    val cancelPromNotif: PendingIntent
        get() {
            val intent = Intent(this, Result::class.java)
            intent.putExtra(MODE, ID_ACCEPT)
            return PendingIntent.getBroadcast(this, 0, intent, FLAGS)
        }

    fun getPostponeSummaryNotif(id: Int, des: String?, link: String?): PendingIntent {
        val intent = Intent(this, Result::class.java)
        intent.putExtra(MODE, ID_SUMMARY_POSTPONE)
        intent.putExtra(Const.DESCTRIPTION, des)
        intent.putExtra(Const.LINK, link)
        intent.putExtra(DataBase.ID, id)
        return PendingIntent.getBroadcast(this, id, intent, FLAGS)
    }

    fun cancel(id: Int) {
        manager.cancel(id)
    }

    @RequiresApi(26)
    private fun createChannels() {
        val chSummary = NotificationChannel(
            CHANNEL_SUMMARY,
            getString(R.string.notif_new), NotificationManager.IMPORTANCE_HIGH
        )
        chSummary.enableLights(true)
        chSummary.lightColor = Color.GREEN
        chSummary.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        manager.createNotificationChannel(chSummary)
        val chProm = NotificationChannel(
            CHANNEL_PROM,
            getString(R.string.notif_prom), NotificationManager.IMPORTANCE_HIGH
        )
        chSummary.enableLights(true)
        chProm.lightColor = Color.RED
        chProm.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        manager.createNotificationChannel(chProm)
        val chTips = NotificationChannel(
            CHANNEL_TIPS,
            getString(R.string.tips), NotificationManager.IMPORTANCE_HIGH
        )
        chTips.setSound(null, null)
        chTips.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        manager.createNotificationChannel(chTips)
        val chMute = NotificationChannel(
            CHANNEL_MUTE,
            getString(R.string.mute_notif), NotificationManager.IMPORTANCE_LOW
        )
        chMute.setSound(null, null)
        chMute.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        manager.createNotificationChannel(chMute)
    }

    /**
     * Get a notification of type 1
     *
     *
     * Provide the builder rather than the notification it's self as useful for making notification
     * changes.
     *
     * @param title the title of the notification
     * @param msg   the text text for the notification
     * @return the builder as it keeps a reference to the notification (since API 24)
     */
    fun getNotification(title: String, msg: String, channel: String): NotificationCompat.Builder {
        val notifBuilder = NotificationCompat.Builder(applicationContext, channel)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            if (notifList == null) notifList = ArrayList()
            notifList?.add("$title $msg")
        }
        notifBuilder.setContentTitle(title)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setContentText(msg)
            .setSmallIcon(R.drawable.star)
            .setAutoCancel(true)
        if (msg.length > 44)
            notifBuilder.setStyle(NotificationCompat.BigTextStyle().bigText(msg))
        return notifBuilder
    }

    fun getSummaryNotif(title: String, channel: String): NotificationCompat.Builder {
        val notifBuilder = NotificationCompat.Builder(this, channel)
        val style = NotificationCompat.InboxStyle()
            .setSummaryText(title)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            style.setBigContentTitle(getString(R.string.app_name))
            notifList?.run {
                forEach { style.addLine(it) }
                clear()
            }
            @SuppressLint("UnspecifiedImmutableFlag")
            val piEmpty = PendingIntent.getActivity(
                this,
                0, Intent(), PendingIntent.FLAG_UPDATE_CURRENT
            )
            notifBuilder.setFullScreenIntent(piEmpty, false)
                .setWhen(System.currentTimeMillis()).setShowWhen(true)
        }
        notifBuilder.setContentTitle(getString(R.string.app_name))
            .setSmallIcon(R.drawable.star)
            .setContentText(title)
            .setStyle(style)
            .setGroupSummary(true)
        return notifBuilder
    }

    /**
     * Send a notification.
     *
     * @param id           The ID of the notification
     * @param notification The notification object
     */
    fun notify(id: Int, notification: NotificationCompat.Builder) {
        manager.notify(id, notification.build())
    }

    class Result : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val mode = intent.getIntExtra(MODE, -1)
            val notifUtils = NotificationUtils()
            if (mode == ID_ACCEPT) {
                notifUtils.cancel(NOTIF_PROM)
            } else if (mode == ID_SUMMARY_POSTPONE) {
                notifUtils.cancel(intent.getIntExtra(DataBase.ID, 0))
                val des = intent.getStringExtra(Const.DESCTRIPTION)
                val link = intent.getStringExtra(Const.LINK)
                if (des == null || link == null) return
                SummaryHelper.postpone(des, link)
            }
        }
    }
}