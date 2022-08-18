package ru.neosvet.vestnewage.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.work.Data
import kotlinx.coroutines.*
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.loader.MasterLoader
import ru.neosvet.vestnewage.loader.SiteLoader
import ru.neosvet.vestnewage.loader.SummaryLoader
import ru.neosvet.vestnewage.loader.basic.LoadHandler
import ru.neosvet.vestnewage.loader.page.PageLoader
import ru.neosvet.vestnewage.loader.page.StyleLoader
import ru.neosvet.vestnewage.network.NetConst
import ru.neosvet.vestnewage.utils.*
import ru.neosvet.vestnewage.view.activity.MainActivity
import ru.neosvet.vestnewage.view.basic.NeoToast
import ru.neosvet.vestnewage.viewmodel.SiteToiler

/**
 * Created by NeoSvet on 19.11.2019.
 */
class LoaderService : LifecycleService(), LoadHandler {
    companion object {
        private const val PROGRESS_ID = 777
        private const val FINAL_ID = 780

        var isRun = false
        private const val STOP = -2
        private const val DOWNLOAD_LIST = 0
        private const val DOWNLOAD_PAGE = 1

        /**
         * @param list contains year (YY) for load or 0 - basic (summary and site), 1 - doctrine
         * @param toast for notify about load background or already run
         */
        @JvmStatic
        fun loadList(list: List<Int>, toast: NeoToast) {
            toast.autoHide = true
            if (isRun) {
                toast.show(App.context.getString(R.string.load_already_run))
                return
            } else toast.show(App.context.getString(R.string.load_background))

            val intent = Intent(App.context, LoaderService::class.java)
            intent.putExtra(Const.MODE, DOWNLOAD_LIST)
            intent.putExtra(Const.LIST, list.toIntArray())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                App.context.startForegroundService(intent)
            else
                App.context.startService(intent)
        }

        @JvmStatic
        fun loadPage(link: String) {
            val intent = Intent(App.context, LoaderService::class.java)
            intent.putExtra(Const.MODE, DOWNLOAD_PAGE)
            intent.putExtra(Const.LINK, link)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                App.context.startForegroundService(intent)
            else
                App.context.startService(intent)
        }
    }

    private lateinit var notif: NotificationCompat.Builder
    private val manager: NotificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }
    private val FLAGS =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) PendingIntent.FLAG_UPDATE_CURRENT
        else PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    private var scope = initScope()
    private fun initScope() = CoroutineScope(Dispatchers.IO
            + CoroutineExceptionHandler { _, throwable ->
        errorHandler(throwable)
    })

    private val loader = MasterLoader(this)
    private val progress = Progress()
    private var mode = 0
    private var request: String? = null
    private var curYear = 0
    private var curMonth = 0

    private fun errorHandler(throwable: Throwable) {
        throwable.printStackTrace()
        scope = initScope()
        stop()
        ErrorUtils.setData(getInputData())
        ErrorUtils.setError(throwable)
        finishService(throwable.localizedMessage)
    }

    private fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, "LoaderService")
        .putInt(Const.MODE, mode)
        .putString(Const.DESCTRIPTION, request ?: "null")
        .build()

    override fun stopService(name: Intent?): Boolean {
        stop()
        return super.stopService(name)
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    private fun stop() {
        isRun = false
        loader.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        when (intent.getIntExtra(Const.MODE, STOP)) {
            STOP -> {
                stop()
                return START_NOT_STICKY
            }
            DOWNLOAD_PAGE -> {
                initStart()
                intent.getStringExtra(Const.LINK)?.let {
                    loadLink(it)
                }
                runUpdateNotifTimer()
            }
            DOWNLOAD_LIST -> {
                initStart()
                intent.getIntArrayExtra(Const.LIST)?.let {
                    loadList(it)
                }
                runUpdateNotifTimer()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun loadList(list: IntArray) {
        scope.launch {
            setCurrentDate()
            calcMaxProgress(list)
            val styleLoader = StyleLoader()
            styleLoader.download(false)
            list.forEach {
                when (it) {
                    0 -> loadBasic()
                    1 -> loadDoctrine()
                    else -> loadYear(it + 2000)
                }
                if (isRun.not()) return@launch
            }
            finishService(null)
        }
    }

    private fun setCurrentDate() {
        val d = DateUnit.initToday()
        curYear = d.year
        curMonth = d.month
    }

    private fun loadLink(link: String) {
        scope.launch {
            val styleLoader = StyleLoader()
            styleLoader.download(false)
            val loader = PageLoader()
            loader.download(link, true)
            loader.finish()
            finishService(null)
        }
    }

    private fun initStart() {
        isRun = true
        setMax(0)
        progress.text = getString(R.string.start)
        initNotif()
    }

    private fun runUpdateNotifTimer() {
        val scope = CoroutineScope(Dispatchers.Default)
        val scopeMain = CoroutineScope(Dispatchers.Main)
        scope.launch {
            val delay = DateUnit.SEC_IN_MILLS.toLong()
            while (isRun) {
                delay(delay)
                if (isRun) scopeMain.launch {
                    notif.setContentText(progress.text)
                    notif.setProgress(progress.max, progress.prog, false)
                    manager.notify(PROGRESS_ID, notif.build())
                }
            }
            stopSelf()
        }
    }

    private fun finishService(error: String?) {
        loader.cancel()
        val notifHelper = NotificationUtils()
        val title: String
        val main: Intent
        val msg: String
        if (error == null) {
            isRun = false
            title = getString(R.string.load_suc_finish)
            msg = ""
            main = Intent(this, MainActivity::class.java)
        } else {
            title = getString(R.string.error_load)
            msg = error + Const.N + getString(R.string.touch_to_send)
            main = Intent(Intent.ACTION_VIEW)
            main.data = Uri.parse(Const.mailto + ErrorUtils.getInformation())
            ErrorUtils.clear()
        }
        val piMain = PendingIntent.getActivity(this, 0, main, FLAGS)
        val piEmpty = PendingIntent.getActivity(this, 0, Intent(), FLAGS)
        notif = notifHelper.getNotification(title, msg, NotificationUtils.CHANNEL_TIPS)
            .setContentIntent(piMain)
            .setFullScreenIntent(piEmpty, true)
        manager.notify(FINAL_ID, notif.build())
        stopSelf()
    }

    private fun initNotif() {
        manager.cancel(FINAL_ID)
        val notifHelper = NotificationUtils()
        val main = Intent(this, MainActivity::class.java)
        val piMain = PendingIntent.getActivity(this, 0, main, FLAGS)
        val iStop = Intent(this, LoaderService::class.java)
        iStop.putExtra(Const.MODE, STOP)
        val piStop = PendingIntent.getService(this, 0, iStop, FLAGS)
        notif = notifHelper.getNotification(
            getString(R.string.load),
            getString(R.string.start),
            NotificationUtils.CHANNEL_MUTE
        )
            .setSmallIcon(R.drawable.star_anim)
            .setContentIntent(piMain)
            .setAutoCancel(false)
            .addAction(0, getString(R.string.stop), piStop)
            .setProgress(0, 0, true)
        startForeground(PROGRESS_ID, notif.build())
    }

    private fun loadYear(y: Int) {
        var i = if (y == curYear) curMonth else 12
        val m = if (y == 2004) 7 else 0
        while (i > m && isRun) {
            loader.loadMonth(i, y)
            upProg()
            i--
        }
    }

    private fun loadDoctrine() {
        loader.loadDoctrine()
        upProg()
    }

    private fun loadBasic() {
        val listsUtils = ListsUtils()
        if (listsUtils.summaryIsOld()) {
            val loader = SummaryLoader()
            loader.loadList(false)

        }
        if (listsUtils.siteIsOld())
            loadSiteSection()
        if (isRun.not()) return
        loader.loadSummary()
        if (isRun.not()) return
        loader.loadSite()
        upProg()
    }

    private fun calcMaxProgress(list: IntArray) {
        var max = 0
        val y = curYear - 2000
        list.forEach {
            max += when (it) {
                in 0..1 -> 1
                4 -> 5
                y -> curMonth
                else -> 12
            }
        }
        setMax(max)
    }

    private fun loadSiteSection() {
        val url = arrayOf(
            NetConst.SITE,
            NetConst.SITE + SiteToiler.NOVOSTI
        )
        val file = arrayOf(
            Lib.getFile(SiteToiler.MAIN).toString(),
            Lib.getFile(SiteToiler.NEWS).toString()
        )
        var loader: SiteLoader
        var i = 0
        while (i < url.size && isRun) {
            loader = SiteLoader(file[i])
            loader.load(url[i])
            i++
        }
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
}