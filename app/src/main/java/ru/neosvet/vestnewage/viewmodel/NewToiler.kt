package ru.neosvet.vestnewage.viewmodel

import androidx.work.Data
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.storage.AdsStorage
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
    private val storage = AdsStorage()
    private lateinit var ads: AdsUtils
    private lateinit var poemFrom: String
    private var task = Task.NONE

    fun init() {
        if (isInit) return
        ads = AdsUtils(storage)
        poemFrom = App.context.getString(R.string.poem) + " " + App.context.getString(R.string.from) + " "
        isInit = true
    }

    override fun onDestroy() {
        storage.close()
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
            unread.setBadge(storage.unreadCount)
            if (unread.lastModified() > 0) {
                val links = unread.list
                for (i in links.size - 1 downTo 0) {
                    s = links[i]
                    t = s.substring(s.lastIndexOf(File.separator) + 1)
                    if (t.contains("_")) {
                        n = t.indexOf("_")
                        t = t.substring(0, n) + " (" + t.substring(n + 1) + ")"
                    }
                    if (s.isPoem)
                        t = poemFrom + t
                    if (s.contains("#")) {
                        t = t.replace("#", " (") + ")"
                        list.add(ListItem(t, s.replace("#", Const.HTML + "#")))
                    } else
                        list.add(ListItem(t, s + Const.HTML))
                }
            }
            postState(NeoState.ListValue(list))
            needOpen = false
        }
    }

    fun clearList() {
        task = Task.CLEAR
        scope.launch {
            val unread = UnreadUtils()
            unread.clearList()
            unread.setBadge(storage.unreadCount)
            postState(NeoState.Ready)
        }
    }

    fun readAds(item: ListItem) {
        storage.setRead(item)
    }
}