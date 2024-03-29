package ru.neosvet.vestnewage.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.data.SimpleItem
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.DevStorage
import ru.neosvet.vestnewage.view.dialog.MessageDialog
import ru.neosvet.vestnewage.view.list.BasicAdapter
import ru.neosvet.vestnewage.viewmodel.basic.DevStrings

class AdsUtils(
    private val storage: DevStorage,
    context: Context
) {
    companion object {
        private const val UPDATE = "update"
        private const val GOOGLE = "Google Play"
        private const val HUAWEI = "AppGallery"
    }

    private val strings: DevStrings

    init {
        strings = DevStrings(
            ad = context.getString(R.string.ad),
            ok = context.getString(android.R.string.ok),
            urlOnGoogle = context.getString(R.string.url_on_google),
            urlOnHuawei = context.getString(R.string.url_on_huawei),
            openLink = context.getString(R.string.open_link),
            accessNewVersion = context.getString(R.string.access_new_version),
            currentVersion = context.getString(R.string.current_version)
        )
    }

    fun getItem(index: Int): SimpleItem? {
        val cursor = storage.getAll()
        if (!cursor.moveToFirst()) return null
        val iTitle = cursor.getColumnIndex(Const.TITLE)
        val iDes = cursor.getColumnIndex(Const.DESCRIPTION)
        val iLink = cursor.getColumnIndex(Const.LINK)
        var n = 0
        var item: SimpleItem? = null
        do {
            if (index == n) {
                item = SimpleItem(
                    title = cursor.getString(iTitle),
                    des = cursor.getString(iDes),
                    link = cursor.getString(iLink)
                )
                break
            }
            n++
        } while (cursor.moveToNext())
        cursor.close()
        return item
    }

    @SuppressLint("Range")
    fun getItem(title: String): BasicItem? {
        val cursor = storage.getItem(title)
        val item = if (cursor.moveToFirst()) {
            val link = cursor.getString(cursor.getColumnIndex(Const.LINK))
            val des = cursor.getString(cursor.getColumnIndex(Const.DESCRIPTION))
            val mode = cursor.getInt(cursor.getColumnIndex(Const.MODE))
            createItem(
                title = title,
                link = link,
                des = des,
                mode = mode
            )
        } else null
        cursor.close()
        return item
    }

    fun getUnreadList(): MutableList<BasicItem> {
        val list = mutableListOf<BasicItem>()
        val cursor = storage.getUnread()
        if (!cursor.moveToFirst()) return list
        val iMode = cursor.getColumnIndex(Const.MODE)
        val iTitle = cursor.getColumnIndex(Const.TITLE)
        val iDes = cursor.getColumnIndex(Const.DESCRIPTION)
        val iLink = cursor.getColumnIndex(Const.LINK)
        do {
            createItem(
                title = cursor.getString(iTitle),
                link = cursor.getString(iLink),
                des = cursor.getString(iDes),
                mode = cursor.getInt(iMode)
            )?.let { item ->
                item.title = strings.ad + ": " + item.title
                list.add(0, item)
            }
        } while (cursor.moveToNext())
        cursor.close()
        return list
    }

    fun getFullList(): List<BasicItem> {
        val list = mutableListOf<BasicItem>()
        val cursor = storage.getAll()
        if (!cursor.moveToFirst()) return list
        val iMode = cursor.getColumnIndex(Const.MODE)
        val iTitle = cursor.getColumnIndex(Const.TITLE)
        val iDes = cursor.getColumnIndex(Const.DESCRIPTION)
        val iLink = cursor.getColumnIndex(Const.LINK)
        val iUnread = cursor.getColumnIndex(Const.UNREAD)
        do {
            createItem(
                title = cursor.getString(iTitle),
                link = null,
                des = null,
                mode = cursor.getInt(iMode)
            )?.let { item ->
                val des = cursor.getString(iDes)
                if (item.link == UPDATE) {
                    item.clear()
                    item.addLink(GOOGLE, strings.urlOnGoogle)
                    item.addLink(HUAWEI, strings.urlOnHuawei)
                    item.des = des
                } else cursor.getString(iLink)?.let {
                    item.addLink(it)
                    item.des = if (des.isNullOrEmpty()) it else des
                }
                if (cursor.getInt(iUnread) == 1)
                    item.des = BasicAdapter.LABEL_SEPARATOR + item.des
                list.add(0, item)
            }
        } while (cursor.moveToNext())
        cursor.close()
        return list
    }

    private fun createItem(title: String, link: String?, des: String?, mode: Int): BasicItem? =
        when (mode) {
            DevStorage.TYPE_TIME -> null
            DevStorage.TYPE_UPDATE -> {
                val ver = title.toInt()
                val item = if (ver > App.version)
                    BasicItem(strings.accessNewVersion)
                else BasicItem(strings.currentVersion)
                des?.let { item.addHead(it) }
                item.addLink(UPDATE)
                item
            }

            else -> {
                val item = BasicItem(title)
                //if (mode != DevStorage.TYPE_DES)
                link?.let { item.addLink(it) }
                //if (mode != DevStorage.TYPE_LINK)
                des?.let { item.addHead(it) }
                item
            }
        }

    fun showDialog(act: Activity, item: BasicItem, close: () -> Unit) {
        if (item.head.isEmpty()) { // only link
            Urls.openInApps(item.link)
            close.invoke()
            return
        }

        val alert = MessageDialog(act).apply {
            setTitle(strings.ad)
            setMessage(item.head)
        }
        when {
            item.link.isEmpty() -> // only des
                alert.setRightButton(strings.ok) { alert.dismiss() }

            item.link == UPDATE -> {
                alert.setLeftButton(GOOGLE) {
                    Urls.openInApps(strings.urlOnGoogle)
                    alert.dismiss()
                }
                alert.setRightButton(HUAWEI) {
                    Urls.openInApps(strings.urlOnHuawei)
                    alert.dismiss()
                }
            }

            else -> {
                alert.setRightButton(strings.openLink) {
                    Urls.openInApps(item.link)
                    alert.dismiss()
                }
            }
        }
        alert.show { close.invoke() }
    }
}