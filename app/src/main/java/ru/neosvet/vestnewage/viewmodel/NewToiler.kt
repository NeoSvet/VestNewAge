package ru.neosvet.vestnewage.viewmodel

import android.app.Activity
import androidx.work.Data
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.utils.*
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import java.io.File

class NewToiler : NeoToiler() {
    enum class Task {
        NONE, OPEN, CLEAR
    }

    var needOpen: Boolean = true
    private var isInit = false
    private lateinit var ads: AdsUtils
    private lateinit var poem_from: String
    private var task: Task = Task.NONE

    fun init(act: Activity) {
        if (isInit) return
        ads = AdsUtils(act)
        poem_from = act.getString(R.string.poem) +
                " " + act.getString(R.string.from) + " "
        isInit = true
    }

    override fun onDestroy() {
        ads.close()
    }

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, "New")
        .putString(Const.MODE, task.toString())
        .build()

    fun openList() {
        if (needOpen.not()) return
        task = Task.OPEN
        scope.launch {
            val notifHelper = NotificationUtils()
            notifHelper.cancel(NotificationUtils.NOTIF_SUMMARY)
            val list = ads.loadList(true) as MutableList
            var t: String
            var s: String
            var n: Int
            val unread = UnreadUtils()
            unread.setBadge(ads.unreadCount)
            if (unread.lastModified() > 0) {
                val links = unread.list
                for (i in links.size - 1 downTo -1 + 1) {
                    s = links[i]
                    t = s.substring(s.lastIndexOf(File.separator) + 1)
                    if (t.contains("_")) {
                        n = t.indexOf("_")
                        t = t.substring(0, n) + " (" + t.substring(n + 1) + ")"
                    }
                    if (s.isPoem)
                        t = poem_from + t
                    if (s.contains("#")) {
                        t = t.replace("#", " (") + ")"
                        list.add(ListItem(t, s.replace("#", Const.HTML + "#")))
                    } else
                        list.add(ListItem(t, s + Const.HTML))
                }
                links.clear()
            }
            postState(NeoState.ListValue(list))
            needOpen = false
            if (ads.index > -1)
                showAd(list[ads.index])
        }
    }

    private fun showAd(item: ListItem) {
        val t = item.title
        ads.showAd(
            t.substring(t.indexOf(" ") + 1),
            item.link,
            item.head
        )
    }

    fun clearList() {
        task = Task.CLEAR
        scope.launch {
            val unread = UnreadUtils()
            unread.clearList()
            unread.setBadge(ads.unreadCount)
            postState(NeoState.Ready)
        }
    }

    fun openAd(item: ListItem, pos: Int) {
        task = Task.NONE
        ads.index = pos
        showAd(item)
    }
}