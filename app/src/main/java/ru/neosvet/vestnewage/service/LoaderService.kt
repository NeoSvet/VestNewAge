package ru.neosvet.vestnewage.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.work.Data
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.Section
import ru.neosvet.vestnewage.loader.*
import ru.neosvet.vestnewage.loader.basic.LoadHandler
import ru.neosvet.vestnewage.loader.basic.Loader
import ru.neosvet.vestnewage.loader.page.PageLoader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.utils.*
import ru.neosvet.vestnewage.view.activity.MainActivity
import ru.neosvet.vestnewage.viewmodel.SiteToiler

/**
 * Created by NeoSvet on 19.11.2019.
 */
class LoaderService : LifecycleService(), LoadHandler {
    companion object {
        private const val PROGRESS_ID = 777
        private const val FINAL_ID = 780

        var isRun = false
        const val STOP = -2
        const val DOWNLOAD_ALL = 0
        const val DOWNLOAD_IT = 1
        const val DOWNLOAD_YEAR = 2
        const val DOWNLOAD_PAGE = 3
        const val DOWNLOAD_OTKR = 4

        @JvmStatic
        fun postCommand(mode: Int, request: String?) {
            if (isRun && mode != STOP) {
                Lib.showToast(App.context.getString(R.string.load_already_run))
                return
            }
            val intent = Intent(App.context, LoaderService::class.java)
            intent.putExtra(Const.MODE, mode)
            intent.putExtra(Const.TASK, request)
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

    private val loaderBook: BookLoader by lazy { BookLoader(this) }
    private var curLoader: Loader? = null
    private val progress = Progress()
    private var mode = 0
    private var request: String? = null
    private val curYear: Int by lazy {
        DateUnit.initToday().year
    }
    private val curMonth: Int by lazy {
        DateUnit.initToday().month
    }

    private fun errorHandler(throwable: Throwable) {
        throwable.printStackTrace()
        scope = initScope()
        isRun = false
        curLoader?.cancel()
        ErrorUtils.setData(getInputData())
        if (throwable is Exception)
            ErrorUtils.setError(throwable)
        finishService(throwable.localizedMessage)
    }

    private fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, "LoaderService")
        .putInt(Const.MODE, mode)
        .putString(Const.DESCTRIPTION, request ?: "null")
        .build()

    override fun stopService(name: Intent?): Boolean {
        isRun = false
        curLoader?.cancel()
        return super.stopService(name)
    }

    override fun onDestroy() {
        curLoader?.cancel()
        isRun = false
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        mode = intent.getIntExtra(Const.MODE, STOP)
        if (mode == STOP) {
            isRun = false
            finishService(null)
            return START_NOT_STICKY
        }

        isRun = true
        setMax(0)
        progress.text = getString(R.string.start)
        progress.task = 1
        initNotif()
        Lib.showToast(getString(R.string.load_background))
        request = intent.getStringExtra(Const.TASK)
        scope.launch {
            startLoad()
        }
        runUpdateNotifTimer()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun runUpdateNotifTimer() {
        val handler = Handler {
            notif.setContentText(progress.message)
            notif.setProgress(progress.max, progress.prog, false)
            manager.notify(PROGRESS_ID, notif.build())
            false
        }
        Thread {
            try {
                val delay = DateUnit.SEC_IN_MILLS.toLong()
                while (isRun) {
                    Thread.sleep(delay)
                    if (isRun) handler.sendEmptyMessage(0)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            stopForeground(true)
        }.start()
    }

    private fun finishService(error: String?) {
        curLoader?.cancel()
        val notifHelper = NotificationUtils()
        val title: String
        val main: Intent
        val msg: String
        if (error == null) {
            if (isRun.not()) return
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

    private fun startLoad() {
        progress.count = 0
        when (mode) {
            DOWNLOAD_ALL -> {
                progress.count = 3
                refreshLists(Section.MENU)
                loadLists(Section.MENU)
            }
            DOWNLOAD_IT -> {
                val section = Section.valueOf(request!!)
                if (section == Section.BOOK)
                    progress.count = 3
                else
                    progress.count = 2
                refreshLists(section)
                loadLists(section)
            }
            DOWNLOAD_OTKR -> { //загрузка Посланий за 2004-2015
                curLoader = loaderBook
                loaderBook.loadOldPoslaniya()
            }
            DOWNLOAD_YEAR -> {
                val loader = ListLoader(this)
                curLoader = loader
                loader.loadYear(request!!.toInt())
            }
            DOWNLOAD_PAGE -> {
                val loader = PageLoader()
                loader.download(request!!, true)
                loader.finish()
            }
        }
        finishService(null)
    }

    private fun loadLists(section: Section) {
        val type = when (section) {
            Section.MENU -> {
                loadAllUcoz()
                ListLoader.Type.ALL
            }
            Section.BOOK -> {
                loadAllUcoz()
                ListLoader.Type.BOOK
            }
            Section.SITE -> ListLoader.Type.SITE
            else -> return
        }
        if (isRun.not()) return
        val loader = ListLoader(this)
        curLoader = loader
        loader.loadSection(type)
    }

    private fun loadAllUcoz() {
        setMax((curYear - 2016) * 12)
        loaderBook.loadAllUcoz()
        progress.task++
    }

    private fun refreshLists(section: Section) {
        calcCountLists(section)
        val listsUtils = ListsUtils()
        if (section == Section.MENU) {
            if (listsUtils.summaryIsOld()) {
                val loader = SummaryLoader()
                loader.loadList(false)
            }
            if (isRun.not()) return
            upProg()
        }
        if (section == Section.MENU || section == Section.SITE) {
            if (listsUtils.siteIsOld())
                loadSiteSection()
            if (isRun.not()) return
            upProg()
        }

        if (listsUtils.bookIsOld()) {
            if (section == Section.MENU)
                loadAllCalendar()
            if (section == Section.MENU || section == Section.BOOK) {
                curLoader = loaderBook
                loaderBook.loadNewPoslaniya()
                upProg()
                loaderBook.loadPoemsList(curYear - 1)
            }
        }
        progress.task++
    }

    private fun loadAllCalendar() {
        val loader = CalendarLoader()
        curLoader = loader
        val maxY: Int = curYear + 1
        var maxM = 13
        var y = 2016
        while (y < maxY && isRun) {
            if (y == curYear) maxM = curMonth + 1
            loader.loadListYear(y, maxM)
            upProg()
            y++
        }
    }

    private fun loadSiteSection() {
        val url = arrayOf(
            NeoClient.SITE,
            NeoClient.SITE + SiteToiler.NOVOSTI
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

    private fun calcCountLists(section: Section) {
        val k = when (section) {
            Section.MENU ->  //book (tolk + 2 year), site and rss, calendar from 2016
                5 + curYear - 2015
            Section.BOOK ->
                curYear - 2015 // from 2016
            else -> 1
        }
        progress.text = getString(R.string.download_list)
        setMax(k)
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