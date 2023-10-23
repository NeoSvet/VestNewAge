package ru.neosvet.vestnewage.service

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.LifecycleService
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.NeoList
import ru.neosvet.vestnewage.helper.SummaryHelper
import ru.neosvet.vestnewage.loader.UpdateLoader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Files
import ru.neosvet.vestnewage.utils.NotificationUtils

class CheckService : LifecycleService() {
    companion object {
        @JvmStatic
        fun postCommand(start: Boolean) {
            val intent = Intent(App.context, CheckService::class.java)
            intent.putExtra(Const.START, start)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                App.context.startForegroundService(intent)
            else App.context.startService(intent)
        }
    }

    private var isRun = false

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
        val utils = NotificationUtils()
        val notif = utils.getNotification(
            getString(R.string.app_name),
            getString(R.string.check_new),
            NotificationUtils.CHANNEL_MUTE
        )
            .setProgress(0, 0, true)
        startForeground(NotificationUtils.NOTIF_CHECK, notif.build())
    }

    private fun startLoad() {
        val loader = UpdateLoader(NeoClient())
        try {
            Urls.restore()
            val list = loader.checkSummary(true)
            val pref = getSharedPreferences(SummaryHelper.TAG, Context.MODE_PRIVATE)
            if (pref.getBoolean(Const.MODE, true)) {
                if (loader.checkAddition())
                    list.add(Pair(getString(R.string.new_in_additionally), Files.RSS + "1"))
            }
            if (pref.getBoolean(Const.DOCTRINE, false)) {
                if (loader.checkDoctrine())
                    list.add(Pair(getString(R.string.new_in_doctrine), Files.RSS + "2"))
            }
            if (pref.getBoolean(Const.PLACE, false)) {
                if (loader.checkAcademy())
                    list.add(Pair(getString(R.string.new_in_academy), Files.RSS + "3"))
            }
            if (list.isNotEmpty) pushNotification(list)
        } catch (ignored: Exception) {
        }
        loader.cancel()
        isRun = false
        postCommand(false)
    }

    private fun pushNotification(list: NeoList<Pair<String, String>>) {
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