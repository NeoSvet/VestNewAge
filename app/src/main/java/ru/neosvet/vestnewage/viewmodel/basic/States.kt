package ru.neosvet.vestnewage.viewmodel.basic

import android.os.Build
import androidx.work.Data
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.CalendarItem
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.data.NeoException
import ru.neosvet.vestnewage.utils.Const
import java.net.SocketTimeoutException
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

    class Error(
        private val throwable: Throwable,
        private val data: Data
    ) : NeoState() {
        var message = ""
            private set
        var isNeedReport = false
            private set

        init {
            message = when {
                throwable.localizedMessage.isNullOrEmpty() -> {
                    isNeedReport = true
                    App.context.getString(R.string.unknown_error)
                }
                throwable is SocketTimeoutException || throwable is SSLHandshakeException ||
                        throwable is CertificateException ->
                    App.context.getString(R.string.site_no_response)
                else -> {
                    isNeedReport = throwable !is NeoException
                    throwable.localizedMessage!!
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
        val list: List<CalendarItem>
    ) : NeoState()

    data class Book(
        val date: String,
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