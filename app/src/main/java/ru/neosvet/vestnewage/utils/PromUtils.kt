package ru.neosvet.vestnewage.utils

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.core.net.toUri
import androidx.core.view.isVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.activity.MainActivity
import ru.neosvet.vestnewage.view.basic.BottomAnim
import ru.neosvet.vestnewage.view.dialog.SetNotifDialog
import java.util.Timer
import kotlin.concurrent.timer

class PromUtils(
    private val tvPromTime: TextView?
) {
    companion object {
        const val TAG = "Prom"
        private const val TWO_SEC = (2 * DateUnit.SEC_IN_MILLS).toLong()
        private const val TENTH_MIN = 6 * DateUnit.SEC_IN_MILLS
        private const val TENTH_HOUR = 360 * DateUnit.SEC_IN_MILLS
        private const val DEF_PERIOD = 400000L
    }

    private val isPromField: Boolean
        get() = tvPromTime != null
    private val pref: SharedPreferences =
        App.context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
    private var period = DEF_PERIOD
    private var isStart = false
    private val scope = CoroutineScope(Dispatchers.Default)
    private var timer: Timer? = null

    init {
        if (tvPromTime != null) {
            tvPromTime.isVisible = true
            setViews()
        }
    }

    fun stop() {
        isStart = false
        timer?.cancel()
    }

    fun resume() {
        if (isPromField && !isStart) {
            period = DEF_PERIOD
            runPromTime()
        }
    }

    fun hide() {
        stop()
        tvPromTime?.let {
            it.text = ""
            it.isVisible = false
        }
    }

    fun show() {
        resume()
        tvPromTime?.isVisible = true
    }

    private fun setViews() {
        if (tvPromTime?.id == R.id.tvPromTimeFloat) {
            val anim = BottomAnim(tvPromTime)
            tvPromTime.setOnClickListener {
                anim.hide()
                scope.launch {
                    delay(TWO_SEC)
                    tvPromTime.post { anim.show() }
                }
            }
        } else { //R.id.tvPromTimeInMenu
            tvPromTime?.setOnClickListener {
                openReader(Urls.PROM_LINK, null)
            }
        }
    }

    private fun getPromDate(next: Boolean): DateUnit {
        val timeDiff = pref.getInt(Const.TIMEDIFF, 0)
        val now = DateUnit.initNow()
        val prom = DateUnit.putDays(now.timeInDays)
        prom.createTime()
        prom.changeSeconds(-timeDiff)
        while (now.timeInSeconds > prom.timeInSeconds)
            prom.changeHours(8)
        if (next) prom.changeHours(8)
        return prom
    }

    private fun runPromTime() {
        scope.launch {
            setPromTime()
        }
    }

    private fun setPromTime() {
        isStart = true
        val t = getPromText()
        if (t == null) { //t.contains("-")
            setTimeText(App.context.getString(R.string.prom))
            hideTimeText()
            restartTimer()
            return
        }
        setTimeText(t)
        tvPromTime?.let { tv ->
            if (tv.id == R.id.tvPromTimeFloat && isHours(t)) {
                var n = App.context.getString(R.string.to_prom).length + 1
                n = t.substring(n, n + 1).toInt()
                tv.post { tv.isVisible = n < 3 }
            }
        }
    }

    private fun isHours(t: String): Boolean =
        t.contains(App.context.resources.getStringArray(R.array.time)[6])


    private fun restartTimer() {
        scope.launch {
            delay(TENTH_MIN.toLong())
            tvPromTime?.post { show() }
        }
    }

    private fun hideTimeText() {
        stop()
        tvPromTime?.post {
            val an = AnimationUtils.loadAnimation(App.context, R.anim.hide)
            an.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {}
                override fun onAnimationEnd(animation: Animation) {
                    tvPromTime.isVisible = false
                }

                override fun onAnimationRepeat(animation: Animation) {}
            })
            an.duration = TWO_SEC
            tvPromTime.startAnimation(an)
        }
    }

    private fun setTimeText(t: String) {
        tvPromTime?.post { tvPromTime.text = t }
    }

    private fun getPromText(): String? {
        val prom = getPromDate(false)
        val p = prom.timeInMills
        val now = System.currentTimeMillis()
        val n = (p - now).toInt()
        if (n < DateUnit.SEC_IN_MILLS) return null
        var t = DateUnit.getDiffDate(p, now)
        t = t.replace(
            App.context.resources.getStringArray(R.array.time)[3],
            App.context.getString(R.string.minute)
        )
        t = App.context.getString(R.string.to_prom) + " " + t
        if (isPromField) startTimer(n, t)
        return t
    }

    private fun startTimer(timeDiff: Int, timeUnit: String) {
        val p =
            if (timeUnit.contains(App.context.getString(R.string.sec))) DateUnit.SEC_IN_MILLS
            else if (timeUnit.contains(App.context.getString(R.string.min))) TENTH_MIN
            else TENTH_HOUR
        if (period > p) {
            stop()
            period = p.toLong()
            var d = timeDiff % p - 100
            if (d < 10) d = 0
            timer = timer(initialDelay = d.toLong(), period = period) {
                runPromTime()
            }
        }
    }

    fun showNotif() {
        val p = pref.getInt(Const.TIME, Const.TURN_OFF)
        if (p == Const.TURN_OFF) return
        val sound = pref.getBoolean(SetNotifDialog.SOUND, false)
        val vibration = pref.getBoolean(SetNotifDialog.VIBR, true)
        val intent = Intent(App.context, MainActivity::class.java)
        intent.data = (Urls.Site + Urls.PROM_LINK).toUri()
        val piEmpty = PendingIntent.getActivity(App.context, 0, Intent(), NotificationUtils.FLAGS)
        val piProm = PendingIntent.getActivity(App.context, 0, intent, NotificationUtils.FLAGS)
        val notifUtils = NotificationUtils()
        var msg = getPromText()
        if (msg == null) //text.contains("-")
            msg = App.context.getString(R.string.prom)
        val piCancel = notifUtils.cancelPromNotif
        val notifBuilder = notifUtils.getNotification(
            App.context.getString(R.string.prom_for_soul_unite),
            msg, NotificationUtils.CHANNEL_PROM
        )
        notifBuilder.setContentIntent(piProm)
            .setFullScreenIntent(piEmpty, true)
            .addAction(0, App.context.getString(R.string.accept), piCancel)
            .setLights(Color.GREEN, DateUnit.SEC_IN_MILLS, DateUnit.SEC_IN_MILLS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (p == 0) notifBuilder.setTimeoutAfter((30 * DateUnit.SEC_IN_MILLS).toLong())
            else notifBuilder.setTimeoutAfter(p.toLong() * 60 * DateUnit.SEC_IN_MILLS)
        } else {
            if (sound) {
                val uri = pref.getString(SetNotifDialog.URI, null)
                if (uri == null) notifBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                else notifBuilder.setSound(uri.toUri())
            }
            if (vibration) notifBuilder.setVibrate(longArrayOf(500, 1500))
        }
        notifUtils.notify(NotificationUtils.NOTIF_PROM, notifBuilder)
        initNotif(p)
    }

    fun initNotif(param: Int) {
        val intent = Intent(App.context, Rec::class.java)
        val piProm = PendingIntent.getBroadcast(App.context, 2, intent, NotificationUtils.FLAGS)
        if (param == Const.TURN_OFF) {
            NotificationUtils.setAlarm(piProm, param.toLong())
            return
        }
        val prom = PromUtils(null)
        var d = prom.getPromDate(false)
        val min = param + 1
        d.changeMinutes(-min)
        if (d.timeInSeconds <= DateUnit.initNow().timeInSeconds) {
            d = prom.getPromDate(true)
            d.changeMinutes(-min)
        }
        NotificationUtils.setAlarm(piProm, d.timeInMills)
    }

    class Rec : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val prom = PromUtils(null)
            prom.showNotif()
        }
    }
}