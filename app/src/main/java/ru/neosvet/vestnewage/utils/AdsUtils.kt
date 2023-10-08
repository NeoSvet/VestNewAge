package ru.neosvet.vestnewage.utils

import android.app.Activity
import android.content.ContentValues
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.AdsStorage
import ru.neosvet.vestnewage.storage.DevAds
import ru.neosvet.vestnewage.view.dialog.CustomDialog
import java.io.BufferedReader
import java.io.InputStreamReader

class AdsUtils(private val storage: DevAds) {
    companion object {
        const val TITLE = 0
        const val LINK = 1
        const val DES = 2
        private const val UPDATE = "update"

        fun showDialog(act: Activity, item: BasicItem, close: () -> Unit) {
            if (item.head.isEmpty()) { // only link
                Urls.openInApps(item.link)
                close.invoke()
                return
            }

            val alert = CustomDialog(act).apply {
                setTitle(App.context.getString(R.string.ad))
                setMessage(item.head)
            }
            when {
                item.link.isEmpty() -> // only des
                    alert.setRightButton(App.context.getString(android.R.string.ok)) { alert.dismiss() }

                item.link == UPDATE -> {
                    alert.setLeftButton("Google Play") {
                        Urls.openInApps(App.context.getString(R.string.url_on_google))
                        alert.dismiss()
                    }
                    alert.setRightButton("AppGallery") {
                        Urls.openInApps(App.context.getString(R.string.url_on_huawei))
                        alert.dismiss()
                    }
                }

                else -> {
                    alert.setRightButton(App.context.getString(R.string.open_link)) {
                        Urls.openInApps(item.link)
                        alert.dismiss()
                    }
                }
            }
            alert.show { close.invoke() }
        }
    }

    var warnIndex = -1
        private set
    private var isNew = false
    fun hasNew(): Boolean {
        return isNew
    }

    var time: Long = -1L
        get() {
            if (field == -1L)
                field = storage.getTime()
            return field
        }
        private set

    fun getItem(index: Int): Array<String> {
        val cursor = storage.getAll()
        val m = arrayOf("", "", "")
        if (!cursor.moveToFirst()) return m
        val iTitle = cursor.getColumnIndex(Const.TITLE)
        val iDes = cursor.getColumnIndex(Const.DESCTRIPTION)
        val iLink = cursor.getColumnIndex(Const.LINK)
        var n = 0
        do {
            if (index == n) {
                m[TITLE] = cursor.getString(iTitle)
                m[DES] = cursor.getString(iDes)
                m[LINK] = cursor.getString(iLink)
                break
            }
            n++
        } while (cursor.moveToNext())
        cursor.close()
        return m
    }

    fun loadList(onlyUnread: Boolean): MutableList<BasicItem> {
        val list = mutableListOf<BasicItem>()
        var ad = ""
        val cursor = if (onlyUnread) {
            ad = App.context.getString(R.string.ad) + ": "
            storage.getUnread()
        } else storage.getAll()

        if (!cursor.moveToFirst()) return list
        val iMode = cursor.getColumnIndex(Const.MODE)
        val iTitle = cursor.getColumnIndex(Const.TITLE)
        val iDes = cursor.getColumnIndex(Const.DESCTRIPTION)
        val iLink = cursor.getColumnIndex(Const.LINK)
        val iUnread = cursor.getColumnIndex(Const.UNREAD)
        var m: Byte
        var t: String
        var d: String?
        var l: String?
        var unread: Boolean
        do {
            t = cursor.getString(iTitle)
            d = cursor.getString(iDes)
            l = cursor.getString(iLink)
            m = cursor.getInt(iMode).toByte()
            unread = cursor.getInt(iUnread) == 1
            if (onlyUnread && !unread) continue
            when (m) {
                AdsStorage.MODE_T -> continue
                AdsStorage.MODE_U -> {
                    m = t.toByte()
                    val item = if (m > App.version)
                        BasicItem(ad + App.context.getString(R.string.access_new_version))
                    else
                        BasicItem(ad + App.context.getString(R.string.current_version))
                    item.addHead(d)
                    item.addLink(UPDATE)
                    list.add(0, item)
                }

                else -> {
                    list.add(0, BasicItem(ad + t))
                    if (m != AdsStorage.MODE_TD) list[0].addLink(l)
                    if (m != AdsStorage.MODE_TL) list[0].addHead(d)
                }
            }
            if (!onlyUnread && unread)
                list[0].des = App.context.getString(R.string.new_section)
        } while (cursor.moveToNext())
        cursor.close()
        return list
    }

    private fun update(br: BufferedReader): Boolean {
        var m = arrayOf("", "", "")
        val titles: MutableList<String> = ArrayList()
        var mode: Byte
        var index: Byte = 0
        warnIndex = -1
        var isNew = false
        br.forEachLine {
            when {
                it.contains("<e>") -> {
                    mode = when {
                        m[TITLE].contains("<u>") -> AdsStorage.MODE_U
                        m[LINK].isEmpty() -> AdsStorage.MODE_TD
                        m[DES].isEmpty() -> AdsStorage.MODE_TL
                        else -> AdsStorage.MODE_TLD
                    }
                    if (m[TITLE].contains("<w>")) warnIndex = index.toInt()
                    index++
                    m[TITLE] = m[TITLE].substring(3)
                    titles.add(m[TITLE])
                    if (!storage.existsTitle(m[TITLE])) {
                        isNew = true
                        addRow(mode, m)
                    }
                    m = arrayOf("", "", "")
                }

                it.indexOf("<") != 0 -> //multiline des
                    m[DES] += Const.N + it

                it.contains("<d>") ->
                    m[DES] = it.substring(3)

                it.contains("<l>") ->
                    m[LINK] = it.substring(3)

                else -> m[TITLE] = it
            }
        }
        storage.deleteItems(titles)
        time = storage.newTime()
        return isNew
    }

    private fun addRow(mode: Byte, m: Array<String>) {
        val row = ContentValues()
        row.put(Const.MODE, mode)
        row.put(Const.TITLE, m[TITLE])
        row.put(Const.DESCTRIPTION, m[DES])
        row.put(Const.LINK, m[LINK])
        storage.insert(row)
    }

    fun loadAds(client: NeoClient) {
        isNew = false
        val t = time
        val br = BufferedReader(InputStreamReader(client.getStream(Urls.DevAds)))
        val s = br.readLine()
        if (s.toLong() > t) {
            if (update(br)) {
                isNew = true
                val unread = UnreadUtils()
                unread.setBadge(storage.unreadCount)
            }
        } else time = storage.newTime()
        br.close()
    }
}