package ru.neosvet.vestnewage.viewmodel.basic

import android.os.Build
import androidx.work.Data
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BaseIsBusyException
import ru.neosvet.vestnewage.data.CalendarItem
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.data.MyException
import ru.neosvet.vestnewage.utils.Const
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException

sealed class NeoState {
    object None : NeoState()
    object Loading : NeoState()
    object NoConnected : NeoState()
    data class Progress(val percent: Int) : NeoState()
    data class Message(val message: String) : NeoState()
    object Ready : NeoState()
    object Success : NeoState()
    data class LongValue(val value: Long) : NeoState()

    class Error(val throwable: Throwable, val data: Data) : NeoState() {
        var message = ""
            private set
        var isNeedReport = false
            private set

        init {
            val msg = throwable.localizedMessage
            message = when {
                throwable is BaseIsBusyException ->
                    App.context.getString(R.string.busy_base_error)
                msg.isNullOrEmpty() -> {
                    isNeedReport = true
                    App.context.getString(R.string.unknown_error)
                }
                msg == "timeout" || msg == "Read timed out" ||
                        throwable is SSLHandshakeException || throwable is CertificateException ->
                    App.context.getString(R.string.error_site)
                msg.contains("failed to connect") -> {
                    //failed to connect to blagayavest.info/188.120.225.50 (port 443) from /10.242.40.42 (port 38926) after 20000ms
                    var i = msg.indexOf("connect") + 11
                    val site = msg.substring(i, msg.indexOf("/", i))
                    i = msg.indexOf("after") + 6
                    val sec = msg.substring(i, i + 2)
                    String.format(App.context.getString(R.string.format_timeout), site, sec)
                }
                else -> {
                    isNeedReport = throwable !is MyException
                    msg
                }
            }
        }

        val information: String
            get() {
                val des = StringBuilder()
                des.append(App.context.getString(R.string.error_des))
                des.append(Const.N)
                des.append(throwable.message)
                des.append(Const.N)
                for (e in throwable.stackTrace) {
                    val s = e.toString()
                    if (s.contains("ru.neosvet")) {
                        des.append(s)
                        des.append(Const.N)
                    }
                }
                des.append(Const.N)
                des.append(App.context.getString(R.string.input_data))
                des.append(Const.N)
                val map = data.keyValueMap
                for (key in map.keys) {
                    des.append(key)
                    des.append(": ")
                    des.append(map[key])
                    des.append(Const.N)
                }
                try {
                    des.append(App.context.getString(R.string.srv_info))
                    des.append(
                        String.format(
                            App.context.getString(R.string.format_info),
                            App.context.packageManager.getPackageInfo(
                                App.context.packageName,
                                0
                            ).versionName,
                            App.context.packageManager.getPackageInfo(
                                App.context.packageName,
                                0
                            ).versionCode,
                            Build.VERSION.RELEASE,
                            Build.VERSION.SDK_INT
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return des.toString()
            }
    }

    data class ListValue(
        val list: List<ListItem>
    ) : NeoState()

    data class ListState(
        val event: ListEvent,
        val index: Int = -1
    ) : NeoState()

    data class Calendar(
        val date: String,
        val prev: Boolean,
        val next: Boolean,
        val list: List<CalendarItem>
    ) : NeoState()

    data class Book(
        val date: String,
        val prev: Boolean,
        val next: Boolean,
        val list: List<ListItem>
    ) : NeoState()

    data class Page(
        val url: String,
        val isOtkr: Boolean = false
    ) : NeoState()

    data class Rnd(
        val title: String,
        val link: String,
        val msg: String,
        val place: String,
        val par: Int
    ) : NeoState()

    data class Ads(
        val hasNew: Boolean,
        val warnIndex: Int,
        val timediff: Int
    ) : NeoState()
}

enum class ListEvent {
    REMOTE, CHANGE, MOVE, RELOAD
}