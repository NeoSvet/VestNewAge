package ru.neosvet.vestnewage.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
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
import java.util.concurrent.TimeUnit

class CheckWorker(
    private val context: Context, workerParams: WorkerParameters
) : Worker(context, workerParams) {

    companion object {
        private const val TAG_PERIODIC = "check periodic"

        @JvmStatic
        fun set(time: Int) {
            val work = WorkManager.getInstance(App.context)
            work.cancelAllWorkByTag(TAG_PERIODIC)
            if (time == Const.TURN_OFF) return
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val task = PeriodicWorkRequest.Builder(
                workerClass = CheckWorker::class.java,
                repeatInterval = time.toLong(),
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            )
                .setInitialDelay(time.toLong(), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag(TAG_PERIODIC)
                .build()
            work.enqueue(task)
        }
    }

    private val utils = NotificationUtils()

    override fun doWork(): Result {
        initNotif()
        startLoad()
        return Result.success()
    }

    private fun initNotif() {
        val notif = utils.getNotification(
            context.getString(R.string.app_name),
            context.getString(R.string.check_new),
            NotificationUtils.CHANNEL_MUTE
        )
            .setProgress(0, 0, true)
        utils.notify(NotificationUtils.NOTIF_CHECK, notif)
    }

    private fun startLoad() {
        try {
            Urls.restore()
            val loader = UpdateLoader(NeoClient())
            val list = loader.checkSummary(true)
            val pref = context.getSharedPreferences(SummaryHelper.TAG, Context.MODE_PRIVATE)
            if (pref.getBoolean(Const.MODE, true)) {
                if (loader.checkAddition())
                    list.add(
                        Pair(
                            context.getString(R.string.new_in_additionally),
                            SummaryHelper.TAG + "1"
                        )
                    )
            }
            if (pref.getBoolean(Const.DOCTRINE, false)) {
                if (loader.checkDoctrine())
                    list.add(
                        Pair(
                            context.getString(R.string.new_in_doctrine),
                            SummaryHelper.TAG + "2"
                        )
                    )
            }
            if (pref.getBoolean(Const.PLACE, false)) {
                if (loader.checkAcademy())
                    list.add(
                        Pair(
                            context.getString(R.string.new_in_academy),
                            SummaryHelper.TAG + "3"
                        )
                    )
            }
            if (list.isNotEmpty) pushNotification(list)
        } catch (ignored: Exception) {
        }
        utils.cancel(NotificationUtils.NOTIF_CHECK)
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