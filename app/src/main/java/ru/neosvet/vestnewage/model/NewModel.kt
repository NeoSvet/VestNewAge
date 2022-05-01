package ru.neosvet.vestnewage.model

import android.app.Activity
import androidx.work.Data
import kotlinx.coroutines.launch
import ru.neosvet.utils.Const
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.helpers.DevadsHelper
import ru.neosvet.vestnewage.helpers.NotificationHelper
import ru.neosvet.vestnewage.helpers.UnreadHelper
import ru.neosvet.vestnewage.list.ListItem
import ru.neosvet.vestnewage.model.basic.NeoViewModel
import ru.neosvet.vestnewage.model.basic.Ready
import ru.neosvet.vestnewage.model.basic.SuccessList
import java.io.File

class NewModel : NeoViewModel() {
    var needOpen: Boolean = true
    private var isInit = false
    private lateinit var ads: DevadsHelper
    private lateinit var katren_from: String
    private var mode: String = "none"

    fun init(act: Activity) {
        if (isInit) return
        ads = DevadsHelper(act)
        katren_from = act.getString(R.string.katren) +
                " " + act.getString(R.string.from) + " "
        isInit = true
    }

    override fun onDestroy() {
        ads.close()
    }

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, "New")
        .putString(Const.MODE, mode)
        .build()

    override suspend fun doLoad() {
    }

    fun openList() {
        if(needOpen.not()) return
        mode = "open"
        scope.launch {
            val notifHelper = NotificationHelper()
            notifHelper.cancel(NotificationHelper.NOTIF_SUMMARY)
            val list = ads.loadList(true) as MutableList
            var t: String
            var s: String
            var n: Int
            val unread = UnreadHelper()
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
                    if (s.contains(Const.POEMS))
                        t = katren_from + t
                    if (s.contains("#")) {
                        t = t.replace("#", " (") + ")"
                        list.add(ListItem(t, s.replace("#", Const.HTML + "#")))
                    } else
                        list.add(ListItem(t, s + Const.HTML))
                }
                links.clear()
            }
            mstate.postValue(SuccessList(list))
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
        mode = "clear"
        scope.launch {
            val unread = UnreadHelper()
            unread.clearList()
            unread.setBadge(ads.unreadCount)
            mstate.postValue(Ready)
        }
    }

    fun openAd(item: ListItem, pos: Int) {
        ads.index = pos
        showAd(item)
    }
}