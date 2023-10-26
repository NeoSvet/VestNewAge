package ru.neosvet.vestnewage.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.loader.AdditionLoader
import ru.neosvet.vestnewage.loader.MasterLoader
import ru.neosvet.vestnewage.loader.SiteLoader
import ru.neosvet.vestnewage.loader.UpdateLoader
import ru.neosvet.vestnewage.loader.basic.LoadHandler
import ru.neosvet.vestnewage.loader.basic.LoadHandlerLite
import ru.neosvet.vestnewage.loader.basic.Loader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.ErrorUtils
import ru.neosvet.vestnewage.utils.ListsUtils
import ru.neosvet.vestnewage.utils.NotificationUtils
import ru.neosvet.vestnewage.view.activity.MainActivity
import ru.neosvet.vestnewage.viewmodel.state.BasicState

class LoaderWorker(
    context: Context, workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams), LoadHandler {
    companion object {
        private const val TAG = "loader"
        private const val PROGRESS_ID = 777
        private const val RESULT_ID = 780

        var isRun = false

        /**
         * @param list contains year (YY) for load or 0 - basic (summary and site), 1 - doctrine
         */
        @JvmStatic
        fun load(list: List<Int>) {
            val work = WorkManager.getInstance(App.context)
            work.cancelAllWorkByTag(TAG)
            val data = Data.Builder()
                .putIntArray(Const.LIST, list.toIntArray())
                .build()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val task = OneTimeWorkRequest.Builder(LoaderWorker::class.java)
                .setInputData(data)
                .setConstraints(constraints)
                .addTag(TAG)
                .build()
            work.enqueue(task)
        }
    }

    private enum class Task {
        STARTING, LOAD_BASIC, LOAD_DOCTRINE, LOAD_MONTH
    }

    private lateinit var notif: NotificationCompat.Builder
    private val manager: NotificationManager by lazy {
        context.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
    }
    private val client = NeoClient()
    private val loader = MasterLoader(this)
    private var currentLoader: Loader = loader
    private val progress = Progress()
    private var task = Task.STARTING
    private var todayYear = 0
    private var todayMonth = 0
    private var loadDate = ""

    private fun getData(): Data {
        val builder = Data.Builder()
            .putString(Const.TASK, "LoaderWorker")
            .putString("Status", task.toString())
        if (task == Task.LOAD_BASIC)
            builder.putString(Const.MODE, currentLoader.javaClass.simpleName)
        else if (task == Task.LOAD_MONTH)
            builder.putString(Const.MODE, loadDate)
        return builder.build()
    }

    override suspend fun doWork(): Result {
        isRun = true
        try {
            task = Task.STARTING
            inputData.getIntArray(Const.LIST)?.let {
                initStart()
                runUpdater()
                loadList(it)
            }
            if (isRun) pushNotification(
                title = applicationContext.getString(R.string.load_suc_finish),
                msg = "", intent = Intent(applicationContext, MainActivity::class.java)
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
            val utils = ErrorUtils(ex)
            if (utils.isNotSkip)
                parseError(utils.getErrorState(getData()))
        }
        isRun = false
        return Result.success()
    }

    private fun loadList(list: IntArray) {
        initTodayDate()
        calcMaxProgress(list)
        loader.loadStyle()
        list.forEach {
            when (it) {
                0 -> loadBasic()
                1 -> loadDoctrine()
                else -> loadYear(it + 2000)
            }
            NeoClient.deleteTempFiles()
            if (isRun.not()) return
        }
    }

    private fun initTodayDate() {
        val d = DateUnit.initToday()
        todayYear = d.year
        todayMonth = d.month
    }

    private suspend fun initStart() {
        setMax(0)
        progress.text = applicationContext.getString(R.string.start)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            setForeground(
                ForegroundInfo(
                    PROGRESS_ID,
                    createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
                )
            )
        else setForeground(
            ForegroundInfo(PROGRESS_ID, createNotification())
        )
    }

    private fun runUpdater() {
        CoroutineScope(Dispatchers.Default).launch {
            val delay = DateUnit.SEC_IN_MILLS.toLong()
            while (isRun) {
                delay(delay)
                if (isRun) {
                    notif.setContentText(progress.text)
                    notif.setProgress(progress.max, progress.prog, false)
                    manager.notify(PROGRESS_ID, notif.build())
                }
            }
            currentLoader.cancel()
        }
    }

    private fun parseError(error: BasicState.Error) {
        if (error.isNeedReport) {
            pushNotification(
                title = applicationContext.getString(R.string.error_load),
                msg = error.message + Const.N + applicationContext.getString(R.string.touch_to_send),
                intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(Const.mailto + error.information)
                }
            )
        } else {
            pushNotification(
                title = applicationContext.getString(R.string.error_load),
                msg = error.message,
                intent = Intent(App.context, MainActivity::class.java)
            )
        }
    }

    private fun pushNotification(title: String, msg: String, intent: Intent) {
        val utils = NotificationUtils()
        val piMain =
            PendingIntent.getActivity(applicationContext, 0, intent, NotificationUtils.FLAGS)
        val piEmpty =
            PendingIntent.getActivity(applicationContext, 0, Intent(), NotificationUtils.FLAGS)
        notif = utils.getNotification(title, msg, NotificationUtils.CHANNEL_TIPS)
            .setContentIntent(piMain)
            .setFullScreenIntent(piEmpty, true)
        manager.notify(RESULT_ID, notif.build())
    }

    private fun createNotification(): Notification {
        manager.cancel(RESULT_ID)
        val utils = NotificationUtils()
        val main = Intent(applicationContext, MainActivity::class.java)
        val piMain = PendingIntent.getActivity(applicationContext, 0, main, NotificationUtils.FLAGS)
        val iStop = Intent(applicationContext, Rec::class.java)
        val piStop =
            PendingIntent.getBroadcast(applicationContext, 0, iStop, NotificationUtils.FLAGS)
        notif = utils.getNotification(
            applicationContext.getString(R.string.load),
            applicationContext.getString(R.string.start),
            NotificationUtils.CHANNEL_MUTE
        )
            .setSmallIcon(R.drawable.star_anim)
            .setContentIntent(piMain)
            .setAutoCancel(false)
            .addAction(0, applicationContext.getString(R.string.stop), piStop)
            .setProgress(0, 0, true)
        return notif.build()
    }

    private fun loadYear(y: Int) {
        task = Task.LOAD_MONTH
        currentLoader = loader
        var i = if (y == todayYear) todayMonth else 12
        val m = if (y == 2004) 7 else 0
        while (i > m && isRun) {
            loadDate = "$i.$y"
            loader.loadMonth(i, y)
            upProg()
            i--
        }
    }

    private fun loadDoctrine() {
        task = Task.LOAD_DOCTRINE
        currentLoader = loader
        loader.loadDoctrine()
        upProg()
    }

    private fun loadBasic() {
        task = Task.LOAD_BASIC
        val listsUtils = ListsUtils()
        val updateLoader = UpdateLoader(client)
        currentLoader = updateLoader
        if (listsUtils.summaryIsOld())
            updateLoader.checkSummary(false)
        updateLoader.checkDoctrine()
        updateLoader.checkAcademy()
        if (listsUtils.siteIsOld())
            loadSiteSection()
        if (isRun.not()) return
        currentLoader = loader
        loader.loadSummary()
        if (isRun.not()) return
        loader.loadSite()
        if (isRun.not()) return
        postMessage(applicationContext.getString(R.string.additionally))
        val additionLoader = AdditionLoader(client)
        currentLoader = additionLoader
        var prev = 0
        additionLoader.loadAll(object : LoadHandlerLite {
            override fun postPercent(value: Int) {
                if (prev == value) return
                prev = value
                if (isRun)
                    postMessage(applicationContext.getString(R.string.additionally) + " ($value%)")
            }
        })
        upProg()
    }

    private fun calcMaxProgress(list: IntArray) {
        var max = 0
        val y = todayYear - 2000
        list.forEach {
            max += when (it) {
                in 0..1 -> 1
                4 -> 5
                y -> todayMonth
                else -> 12
            }
        }
        setMax(max)
    }

    private fun loadSiteSection() {
        val loader = SiteLoader(client)
        loader.loadAds()
        if (isRun) loader.loadSite()
    }

    override fun setMax(value: Int) {
        progress.prog = 0
        progress.max = value
    }

    override fun upProg() {
        progress.prog++
    }

    override fun postMessage(value: String) {
        progress.text = value
    }

    class Rec : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            isRun = false
        }
    }
}