package ru.neosvet.vestnewage.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.DataBase
import ru.neosvet.vestnewage.storage.DevStorage
import ru.neosvet.vestnewage.storage.NewsStorage
import ru.neosvet.vestnewage.utils.Files
import ru.neosvet.vestnewage.utils.LaunchUtils
import ru.neosvet.vestnewage.utils.date
import ru.neosvet.vestnewage.utils.hasDate
import ru.neosvet.vestnewage.view.widget.HomeWidget
import java.io.BufferedReader
import java.io.FileReader

class HomeService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ListFactory(applicationContext)//, intent
    }

    inner class ListFactory(
        private val context: Context
        //  intent: Intent
    ) : RemoteViewsFactory {
        private var labels = mutableListOf<String>()
        private var links = mutableListOf<String>()
//        private val widgetId = intent.getIntExtra(
//            AppWidgetManager.EXTRA_APPWIDGET_ID,
//            AppWidgetManager.INVALID_APPWIDGET_ID
//        )

        override fun onCreate() {}

        override fun getCount() = labels.size

        override fun getItemId(position: Int) = position.toLong()

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewAt(position: Int): RemoteViews {
            val item = RemoteViews(
                context.packageName,
                R.layout.item_widget
            )
            if (position >= labels.size) return item
            item.setTextViewText(R.id.text, labels[position])
            if (position >= links.size) return item
            val intent = Intent()
            intent.data = Uri.parse(Urls.Site + links[position])
            item.setOnClickFillInIntent(R.id.text, intent)

            return item
        }

        override fun getViewTypeCount(): Int {
            return 1
        }

        override fun hasStableIds(): Boolean {
            return true
        }

        override fun onDataSetChanged() {
            labels.clear()
            links.clear()
            try {
                val file = Files.slash(HomeWidget.NAME)
                val br = BufferedReader(FileReader(file))
// RSS:
                var s = br.readLine()
                if (s.isEmpty()) {
                    labels.add(context.getString(R.string.nothing))
                    links.add(Files.RSS)
                } else {
                    val link = br.readLine()
                    links.add(link)
                    if (link.hasDate)
                        labels.add(link.date + ": " + s)
                    else labels.add(s)
                }
// NEWS:
                s = br.readLine()
                when (s) {
                    DevStorage.NAME -> {
                        br.readLine()
                        labels.add(context.getString(R.string.new_dev_ads))
                        links.add(LaunchUtils.DEV_TAB)
                    }

                    NewsStorage.NAME -> {
                        labels.add(getNewsLabel(context, br.readLine().toLong()))
                        links.add("novosti.html")
                    }
                }
// Journal:
                s = br.readLine()
                if (s.isEmpty()) {
                    labels.add(context.getString(R.string.nothing))
                    links.add(DataBase.JOURNAL)
                } else {
                    labels.add(context.getString(R.string.last_readed) + " " + s)
                    links.add(br.readLine())
                }
// end
                br.close()
            } catch (ignore: Exception) {
            }
            if (labels.isEmpty())
                labels.add(context.getString(R.string.list_empty))
        }

        private fun getNewsLabel(context: Context, time: Long): String {
            if (time == 0L)
                return context.getString(R.string.nothing)
            val today = DateUnit.initToday()
            val date = DateUnit.putMills(time)
            return context.getString(R.string.ad) + " " + when (date.toShortDateString()) {
                today.toShortDateString() ->
                    context.getString(R.string.new_today).lowercase()

                today.apply { changeDay(-1) }.toShortDateString() ->
                    context.resources.getStringArray(R.array.post_days)[0]

                else -> context.getString(R.string.from) + " " + date.toDateString()
            }
        }

        override fun onDestroy() {}
    }
}