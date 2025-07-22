package ru.neosvet.vestnewage.utils

import android.os.Build
import androidx.work.Data
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.NeoException
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException

class ErrorUtils(private val throwable: Throwable) {
    val message: String
    private var isNeedReport = false
    val isNotSkip: Boolean
        get() = message.isNotEmpty()

    init {
        message = when {
            throwable.javaClass.simpleName.contains("CancelIsolatedRunner") ->
                ""

            throwable.localizedMessage.isNullOrEmpty() -> {
                isNeedReport = true
                App.context.getString(R.string.unknown_error)
            }

            throwable is SocketTimeoutException || throwable is SocketException ||
                    throwable is UnknownHostException ->
                App.context.getString(R.string.site_no_response)

            throwable is SSLHandshakeException || throwable is CertificateException ||
                    throwable is NeoException.SiteNoResponse -> {
                App.needUnsafeClient()
                App.context.getString(R.string.site_no_response)
            }

            else -> {
                isNeedReport = throwable !is NeoException
                throwable.localizedMessage ?: throwable.message!!
            }
        }
    }

    fun getErrorState(data: Data): BasicState.Error {
        val info = if (isNeedReport)
            getInformation(data) else ""
        return BasicState.Error(message, info)
    }

    private fun getInformation(data: Data): String {
        val des = StringBuilder()
        des.append(App.context.getString(R.string.error_type))
        des.append(" " + throwable.javaClass.name)
        des.append(Const.CRLF)
        des.append(App.context.getString(R.string.error_des))
        des.append(Const.CRLF)
        if (throwable.message.isNullOrBlank())
            des.append(App.context.getString(R.string.unknown_error))
        else des.append(throwable.message)
        des.append(Const.CRLF)
        if (throwable.stackTrace.isNotEmpty()) try {
            var empty = true
            for (item in throwable.stackTrace) {
                val s = item.toString()
                if (s.contains("ru.neosvet")) {
                    empty = false
                    des.append(s)
                    des.append(Const.CRLF)
                }
            }
            if (empty) {
                for (item in throwable.stackTrace) {
                    des.append(item.toString())
                    des.append(Const.CRLF)
                }
            }
        } catch (e: Exception) {
            des.append("Error in stack: ${e.message}")
            des.append(Const.CRLF)
        } else {
            des.append("Stack is empty")
            des.append(Const.CRLF)
        }
        des.append(Const.CRLF)
        des.append(App.context.getString(R.string.input_data))
        des.append(Const.CRLF)
        des.append("COM: ")
        des.append(Urls.isSiteCom)
        des.append(Const.CRLF)
        val map = data.keyValueMap
        for (key in map.keys) {
            des.append(key)
            des.append(": ")
            des.append(map[key])
            des.append(Const.CRLF)
        }
        try {
            des.append(Const.CRLF)
            des.append(App.context.getString(R.string.srv_info))
            des.append(Const.CRLF)
            des.append(
                String.format(
                    App.context.getString(R.string.format_info),
                    App.context.packageManager.getPackageInfo(
                        App.context.packageName,
                        0
                    ).versionName,
                    App.version,
                    Build.VERSION.RELEASE,
                    Build.VERSION.SDK_INT
                )
            )
        } catch (e: Exception) {
            des.append("Error in info: ${e.message}")
        }
        return des.toString()
    }
}