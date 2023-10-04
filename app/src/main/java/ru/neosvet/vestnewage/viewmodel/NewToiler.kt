package ru.neosvet.vestnewage.viewmodel

import android.content.Context
import androidx.work.Data
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.storage.AdsStorage
import ru.neosvet.vestnewage.utils.AdsUtils
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.NotificationUtils
import ru.neosvet.vestnewage.utils.UnreadUtils
import ru.neosvet.vestnewage.utils.isPoem
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.ListState
import java.io.File

class NewToiler : NeoToiler() {
    enum class Task {
        NONE, OPEN, CLEAR
    }

    private var needOpen: Boolean = true
    private val storage = AdsStorage()
    private lateinit var ads: AdsUtils
    private lateinit var poemFrom: String
    private var task = Task.NONE

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, "New")
        .putString(Const.MODE, task.toString())
        .build()

    override fun init(context: Context) {
        ads = AdsUtils(storage.dev)
        poemFrom =
            context.getString(R.string.poem) + " " + context.getString(R.string.from) + " "
    }

    override suspend fun defaultState() {
        openList()
    }

    override fun onDestroy() {
        storage.close()
    }

    fun openList() {
        if (needOpen.not()) return
        task = Task.OPEN
        scope.launch {
            val notifUtils = NotificationUtils()
            notifUtils.cancel(NotificationUtils.NOTIF_SUMMARY)
            val list = ads.loadList(true)
            var t: String
            var s: String
            var n: Int
            val unread = UnreadUtils()
            unread.setBadge(storage.dev.unreadCount)
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
                        list.add(BasicItem(t, s.replace("#", Const.HTML + "#")))
                    } else
                        list.add(BasicItem(t, s + Const.HTML))
                }
            }
            if (list.isEmpty())
                postState(BasicState.Empty)
            else
                postState(ListState.Primary(list = list))
            needOpen = false
        }
    }

    fun clearList() {
        task = Task.CLEAR
        scope.launch {
            val unread = UnreadUtils()
            unread.clearList()
            unread.setBadge(0)
            postState(BasicState.Empty)
        }
    }

    fun readAds(item: BasicItem) {
        storage.dev.setRead(item)
    }
}