package ru.neosvet.vestnewage.view.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.network.OnlineObserver
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.WordsUtils
import ru.neosvet.vestnewage.view.activity.MainActivity

class WordsWidget : AppWidgetProvider() {
    companion object {
        private const val ACTION_COPY = "copy"
        private const val ACTION_SCROLL = "scroll"
        private var words = ""
        private var scrollPos = 0
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val utils = WordsUtils()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        appWidgetIds.forEach { id ->
            RemoteViews(
                context.packageName,
                R.layout.words_widget
            ).apply {
// words
                if (words.isEmpty())
                    words = utils.getGodWords().replace(Const.N, " ")
                if (scrollPos > 0 && words.contains(" ")) {
                    setTextViewText(R.id.tv_words, cropWords())
                } else setTextViewText(R.id.tv_words, words)

                if (words == context.getString(R.string.load)) {
                    appWidgetManager.updateAppWidget(id, this@apply)
                    return
                }
                val intentWords = Intent(context, WordsWidget::class.java)
                intentWords.action = ACTION_COPY
                intentWords.putExtra(
                    Const.DESCTRIPTION,
                    context.getString(R.string.god_words) + ":" + Const.N + words
                )
                intentWords.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                val clickWords = PendingIntent.getBroadcast(
                    context, id, intentWords,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.tv_words, clickWords)
// icon
                val intentIcon = Intent(context, MainActivity::class.java)
                val clickIcon = PendingIntent.getActivity(
                    context, id, intentIcon,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.icon, clickIcon)
// refresh
                val intentRefresh = Intent(context, WordsWidget::class.java)
                intentRefresh.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                intentRefresh.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                val clickRefresh = PendingIntent.getBroadcast(
                    context, id, intentRefresh,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.btn_refresh, clickRefresh)
// scroll
                val intentScroll = Intent(context, WordsWidget::class.java)
                intentScroll.action = ACTION_SCROLL
                intentScroll.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                val clickScroll = PendingIntent.getBroadcast(
                    context, id, intentScroll,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.btn_scroll, clickScroll)
                setTextViewText(
                    R.id.btn_scroll, context.getString(R.string.to_bottom) +
                            " / " + context.getString(R.string.to_top)
                )
// end
                appWidgetManager.updateAppWidget(id, this@apply)
            }
        }
    }

    private fun cropWords(): String {
        var i = words.length / 3
        if (scrollPos > 0) i *= scrollPos
        while (i > 0 && words[i] != ' ') i--
        return words.substring(i + 1)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_COPY -> {
                goCopy(context, intent.getStringExtra(Const.DESCTRIPTION) ?: words)
                return
            }

            ACTION_SCROLL -> {
                scrollPos++
                if (scrollPos == 3) scrollPos = 0
                getId(intent)?.let {
                    onUpdate(context, AppWidgetManager.getInstance(context), it)
                }
                return
            }

            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> scrollPos = 0

            AppWidgetManager.ACTION_APPWIDGET_ENABLED -> { //create widget
                words = ""
                return
            }

            else -> return
        }

        if (words == context.getString(R.string.load)) return
        val manager = AppWidgetManager.getInstance(context)
        val id = getId(intent) ?: manager.getAppWidgetIds(
            ComponentName(
                context,
                WordsWidget::class.java
            )
        )
        words = context.getString(R.string.load)

        scope.launch {
            if (OnlineObserver.isOnline.value) {
                id?.let { onUpdate(context, manager, it) }
                utils.update()
            }
            words = ""
            id?.let { onUpdate(context, manager, it) }
        }
    }

    private fun goCopy(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(context.getString(R.string.app_name), text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_LONG).show()
    }

    private fun getId(intent: Intent): IntArray? {
        val id = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (id == AppWidgetManager.INVALID_APPWIDGET_ID)
            return null
        return intArrayOf(id)
    }
}