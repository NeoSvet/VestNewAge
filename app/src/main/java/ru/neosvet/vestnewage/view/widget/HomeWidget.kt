package ru.neosvet.vestnewage.view.widget

import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.loader.SiteLoader
import ru.neosvet.vestnewage.loader.UpdateLoader
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.OnlineObserver
import ru.neosvet.vestnewage.service.HomeService
import ru.neosvet.vestnewage.storage.DevStorage
import ru.neosvet.vestnewage.storage.JournalStorage
import ru.neosvet.vestnewage.storage.NewsStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Files
import ru.neosvet.vestnewage.view.activity.MainActivity
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileReader
import java.io.FileWriter

class HomeWidget : AppWidgetProvider() {
    companion object {
        const val NAME = "home_widget"
        private const val ACTION_REFRESH = "refresh"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var refreshed = ""
    private var label = ""

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        appWidgetIds.forEach { id ->
            RemoteViews(
                context.packageName,
                R.layout.home_widget
            ).apply {
                setTextViewText(R.id.tv_label, label)
// icon
                val intentIcon = Intent(context, MainActivity::class.java)
                val clickIcon = PendingIntent.getActivity(
                    context, id, intentIcon,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.icon, clickIcon)
// init list
                val intentList = Intent(context, HomeService::class.java)
                intentList.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                setRemoteAdapter(R.id.lv_list, intentList)
                val clickListTemplate = Intent(context, MainActivity::class.java)
                val clickList = TaskStackBuilder.create(context)
                    .addNextIntentWithParentStack(clickListTemplate)
                    .getPendingIntent(
                        0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )
                setPendingIntentTemplate(R.id.lv_list, clickList)
// refresh button
                val intentRefresh = Intent(context, HomeWidget::class.java)
                intentRefresh.action = ACTION_REFRESH
                intentRefresh.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                val clickRefresh = PendingIntent.getBroadcast(
                    context, id, intentRefresh,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.btn_refresh, clickRefresh)
// end
                appWidgetManager.updateAppWidget(id, this@apply)
                appWidgetManager.notifyAppWidgetViewDataChanged(id, R.id.lv_list)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (refreshed.isEmpty())
            refreshed = context.getString(R.string.refreshed)

        if (intent.action != ACTION_REFRESH) {
            if (!Files.slash(NAME).exists()) generateFile()
            return
        }

        val id = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (id == AppWidgetManager.INVALID_APPWIDGET_ID)
            return

        scope.launch {
            if (OnlineObserver.isOnline.value) refresh()
            generateFile()

            onUpdate(context, AppWidgetManager.getInstance(context), intArrayOf(id))
        }
    }

    private fun generateFile() {
        val file = Files.slash(NAME)
        val bw = BufferedWriter(FileWriter(file))
        writeRss(bw)
        writeNews(bw)
        writeJournal(bw)
        bw.close()
        label = refreshed + Const.N + DateUnit.putMills(file.lastModified()).toString()
    }

    private fun writeJournal(bw: BufferedWriter) {
        val journal = JournalStorage()
        journal.getLastItem()?.let {
            bw.write(it.first) //title
            bw.newLine()
            bw.write(it.second) //link
            bw.newLine()
        }
        journal.close()
        bw.newLine()
    }

    private fun writeRss(bw: BufferedWriter) {
        val f = Files.file(Files.RSS)
        if (f.exists()) {
            val br = BufferedReader(FileReader(f))
            bw.write(br.readLine()) //title
            bw.newLine()
            bw.write(br.readLine()) //link
            br.close()
        }
        bw.newLine()
    }

    private fun writeNews(bw: BufferedWriter) {
        val storage = NewsStorage()
        val cursor = storage.getAll()
        var time = 0L
        if (cursor.moveToFirst()) {
            val iTime = cursor.getColumnIndex(Const.TIME)
            if (cursor.moveToNext())
                time = cursor.getLong(iTime)
        }
        cursor.close()
        storage.close()

        if (time == 0L || DateUnit.isVeryLongAgo(time)) {
            val dev = DevStorage()
            if (dev.unreadCount > 0) bw.write(DevStorage.NAME)
            else bw.write(NewsStorage.NAME)
            dev.close()
        } else bw.write(NewsStorage.NAME)
        bw.newLine()
        bw.write(time.toString())
        bw.newLine()
    }

    private fun refresh() {
        val client = NeoClient()
        val update = UpdateLoader(client)
        update.checkSummary(true)
        val site = SiteLoader(client)
        site.loadAds()
        val storage = DevStorage()
        val t = storage.getTime()
        storage.close()
        if (DateUnit.isLongAgo(t))
            site.loadDevAds()
    }
}