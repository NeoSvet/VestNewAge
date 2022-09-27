package ru.neosvet.vestnewage.utils

import android.os.Build
import androidx.work.Data
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.NeoException
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
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
                    throwable is UnknownHostException || throwable is SSLHandshakeException ||
                    throwable is CertificateException ->
                App.context.getString(R.string.site_no_response)
            else -> {
                isNeedReport = throwable !is NeoException
                throwable.localizedMessage!!
            }
        }
    }

    fun getErrorState(data: Data): NeoState.Error {
        val info = if (isNeedReport)
            getInformation(data) else ""
        return NeoState.Error(message, info)
    }

    private fun getInformation(data: Data): String {
        val des = StringBuilder()
        des.append(App.context.getString(R.string.error_des))
        des.append(Const.N)
        des.append(throwable.message)
        des.append(Const.N)
        try {
            for (item in throwable.stackTrace) {
                val s = item.toString()
                if (s.contains("ru.neosvet")) {
                    des.append(s)
                    des.append(Const.N)
                }
            }
        } catch (e: Exception) {
            des.append("Error in stack: ${e.message}")
            des.append(Const.N)
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
            des.append("Error in info: ${e.message}")
        }
        return des.toString()
    }
}