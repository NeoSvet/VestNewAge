package ru.neosvet.vestnewage.utils

import android.os.Build
import androidx.work.Data
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BaseIsBusyException
import ru.neosvet.vestnewage.data.MyException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException

object ErrorUtils {
    private var error: Throwable? = null
    private var data: Data? = null

    @JvmStatic
    var message = ""
        private set
    var isNeedReport = false
        private set

    @JvmStatic
    fun clear() {
        data = null
        error = null
    }

    fun setData(data: Data?) {
        ErrorUtils.data = data
    }

    fun setError(error: Throwable?) {
        ErrorUtils.error = error
        initMessage()
    }

    private fun initMessage() {
        isNeedReport = false
        val msg = error?.localizedMessage
        message = when {
            error == null -> ""
            error is BaseIsBusyException ->
                App.context.getString(R.string.busy_base_error)
            msg.isNullOrEmpty() -> {
                isNeedReport = true
                App.context.getString(R.string.unknown_error)
            }
            msg == "timeout" || msg == "Read timed out" ||
                    error is SSLHandshakeException || error is CertificateException ->
                App.context.getString(R.string.error_site)
            msg.contains("failed to connect") -> {
                var i = msg.indexOf("connect") + 11
                val site = msg.substring(i, msg.indexOf("/", i))
                i = msg.indexOf("after") + 6
                val sec = msg.substring(i, i + 2)
                String.format(App.context.getString(R.string.format_timeout), site, sec)
            }
            else -> {
                isNeedReport = error !is MyException
                msg
            }
        }
    }

    @JvmStatic
    val information: String
        get() {
            val des = StringBuilder()
            if (error != null) {
                des.append(App.context.getString(R.string.error_des))
                des.append(Const.N)
                des.append(error!!.message)
                des.append(Const.N)
                for (e in error!!.stackTrace) {
                    val s = e.toString()
                    if (s.contains("ru.neosvet")) {
                        des.append(s)
                        des.append(Const.N)
                    }
                }
            }
            if (data != null) {
                des.append(Const.N)
                des.append(App.context.getString(R.string.input_data))
                des.append(Const.N)
                val map = data!!.keyValueMap
                for (key in map.keys) {
                    des.append(key)
                    des.append(": ")
                    des.append(map[key])
                    des.append(Const.N)
                }
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