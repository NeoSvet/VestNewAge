package ru.neosvet.vestnewage.helper

import android.content.Context
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.utils.Const

class CabinetHelper(context: Context) {
    companion object {
        const val TAG = "Cabinet"

        var cookie = ""
        var isAlterPath = false

        fun codingUrl(url: String) = if (isAlterPath) {
            Urls.AlterUrl + url.substring(url.indexOf(".com") + 4)
        } else url
    }

    private val pref = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
    var email: String = ""

    fun getAuthPair(): Pair<String, String> {
        pref.getString(Const.EMAIL, "")?.let { email ->
            if (email.isNotEmpty()) {
                pref.getString(Const.PASSWORD, "")?.let { pass ->
                    if (pass.isNotEmpty())
                        return Pair(email, decryptPassword(pass))
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

    private fun encryptPassword(password: String): String {
        val c = password.toCharArray()
        val s = StringBuilder()
        for (a in c.indices) {
            s.append((Character.codePointAt(c, a) - a - 1).toChar())
        }
        return s.toString()
    }

    private fun decryptPassword(password: String?): String {
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
                editor.putString(Const.PASSWORD, encryptPassword(password))
            editor.apply()
        }
    }

    fun clear() {
        cookie = ""
        email = ""
    }
}