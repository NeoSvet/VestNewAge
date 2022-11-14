package ru.neosvet.vestnewage.service

import android.content.Context
import androidx.work.*
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.utils.Const
import java.util.concurrent.TimeUnit

class CheckStarter(
    context: Context, workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        CheckService.postCommand(true)
        return Result.success()
    }

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
                CheckStarter::class.java,
                time.toLong(),
                TimeUnit.MINUTES,
                (time - 5).toLong(),
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag(TAG_PERIODIC)
                .build()
            work.enqueue(task)
        }
    }
}