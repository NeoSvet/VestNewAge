package ru.neosvet.vestnewage.helper

import android.content.Context
import android.os.Build
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.NetConst
import ru.neosvet.vestnewage.network.UnsafeClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.utils.Const

class CabinetHelper(context: Context) {
    companion object {
        const val TAG = "Cabinet"

        var cookie = ""
        var alterCookie = ""
        var alterUrl = ""

        fun codingUrl(url: String) = if (alterUrl.isNotEmpty()) {
            url.replace(Urls.MainSite, alterUrl) + "?__cpo=aHR0cHM6Ly93d3cub3Rrcm92ZW5peWEuY29t"
 //cpo = Base64.encodeToString(Urls.MainSite.substring(0,Urls.MainSite.length-1).toByteArray(), Base64.DEFAULT)
        } else url

        fun createHttpClient(): OkHttpClient =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                NeoClient.createHttpClient()
            else UnsafeClient.createHttpClient()
    }

    private val pref = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
    var email: String = ""

    fun getAuthPair(): Pair<String, String> {
        pref.getString(Const.EMAIL, "")?.let { email ->
            if (email.isNotEmpty()) {
                pref.getString(Const.PASSWORD, "")?.let { pass ->
                    if (pass.isNotEmpty())
                        return Pair(email, uncriptPassword(pass))
                }
                return Pair(email, "")
            }
        }
        return Pair("", "")
    }

    fun forget(isEmail: Boolean, isPassword: Boolean) {
        if (isPassword) {
            val editor = pref.edit()
            editor.putString(Const.PASSWORD, "")
            if (isEmail)
                editor.putString(Const.EMAIL, "")
            editor.apply()
        }
    }

    private fun criptPassword(password: String): String {
        val c = password.toCharArray()
        val s = StringBuilder()
        for (a in c.indices) {
            s.append((Character.codePointAt(c, a) - a - 1).toChar())
        }
        return s.toString()
    }

    private fun uncriptPassword(password: String?): String {
        return try {
            val c = password!!.toCharArray()
            val s = StringBuilder()
            for (a in c.indices) {
                s.append((Character.codePointAt(c, a) + a + 1).toChar())
            }
            s.toString()
        } catch (e: Exception) {
            ""
        }
    }

    fun save(email: String, password: String) {
        if (email.isNotEmpty()) {
            val editor = pref.edit()
            editor.putString(Const.EMAIL, email)
            if (password.isNotEmpty())
                editor.putString(Const.PASSWORD, criptPassword(password))
            editor.apply()
        }
    }

    fun clear() {
        cookie = ""
        email = ""
    }

    fun initAlterPath() {
        val requestBody = FormBody.Builder()
            .add("url", Urls.MainSite)
            .add("proxyServerId", "150")
            .add("demo", "0")
            .add("frontOrigin", "https://www.croxyproxy.com")
            .build()
        val request = Request.Builder()
            .post(requestBody)
            .url("https://www.croxyproxy.com/requests?fso=")
            .addHeader(NetConst.USER_AGENT, App.context.packageName)
            .build()
        val client = createHttpClient()
        val response = client.newCall(request).execute()
        alterUrl = "https://" + response.request.url.host + "/"
        response.headers.forEach {
            if (it.second.contains("__cpc="))
                alterCookie = it.second
        }
        val request2 = Request.Builder()
            .url(response.request.url)
            .addHeader(NetConst.USER_AGENT, App.context.packageName)
            .addHeader(NetConst.COOKIE, alterCookie)
            .build()
        client.newCall(request2).execute()
    }
}