package ru.neosvet.vestnewage.viewmodel

import android.content.Context
import android.view.inputmethod.EditorInfo
import androidx.work.Data
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.http.promisesBody
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.data.CabinetScreen
import ru.neosvet.vestnewage.data.NeoException
import ru.neosvet.vestnewage.helper.CabinetHelper
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.NetConst
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.view.list.CabinetAdapter
import ru.neosvet.vestnewage.viewmodel.basic.CabinetStrings
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.CabinetState
import java.io.BufferedReader
import java.io.InputStreamReader

class CabinetToiler : NeoToiler() {
    companion object {
        private const val ERROR_BOX = "lt_box"
        private const val WORDS_BOX = "name=\"keyw"
        private const val SELECTED = "selected>"
    }

    sealed class Action {
        data class Login(val email: String, val password: String) : Action() {
            override fun toString(): String {
                return "Login"
            }
        }

        data class Word(val index: Int, val word: String) : Action()
        data object Anketa : Action()
        data object None : Action()
    }

    private var screen: CabinetScreen = CabinetScreen.LOGIN
    private lateinit var helper: CabinetHelper
    private var action: Action = Action.None
    private lateinit var strings: CabinetStrings
    private val wordList: MutableList<BasicItem> by lazy { mutableListOf() }
    private val loginList = mutableListOf<BasicItem>()
    private val cabinetList = mutableListOf<BasicItem>()
    private val cabinetItem = BasicItem("")

    override fun getInputData() = Data.Builder()
        .putString(Const.TASK, CabinetHelper.TAG)
        .putString(Const.LIST, screen.toString())
        .putString(Const.MODE, action.toString())
        .build()

    override fun init(context: Context) {
        helper = CabinetHelper(context)
        strings = CabinetStrings(
            selectedStatus = context.getString(R.string.selected_status),
            authFailed = context.getString(R.string.auth_failed),
            profileFailed = context.getString(R.string.profile_failed),
            sendStatus = context.getString(R.string.send_status),
            selectStatus = context.getString(R.string.select_status),
            sendUnlivable = context.getString(R.string.send_unlivable)
        )

        addLoginItems(context)
        val m = context.resources.getStringArray(R.array.cabinet_main)
        var i = 0
        while (i < m.size) {
            loginList.add(BasicItem(m[i]).apply {
                des = m[i + 1]
            })
            i += 2
        }
        cabinetList.add(cabinetItem)
        for (s in context.resources.getStringArray(R.array.cabinet_enter))
            cabinetList.add(BasicItem(s))
    }

    private fun addLoginItems(context: Context) {
        val p = helper.getAuthPair()
        val email = BasicItem(
            context.getString(R.string.email),
            EditorInfo.IME_ACTION_NEXT.toString() + "33"
        ).apply {
            des = p.first
            addHead(CabinetAdapter.TYPE_INPUT)
        }
        loginList.add(email)
        val pass = BasicItem(
            context.getString(R.string.password),
            EditorInfo.IME_ACTION_DONE.toString() + "129"
        ).apply {
            des = p.second
            addHead(CabinetAdapter.TYPE_INPUT)
        }
        loginList.add(pass)
        val remEmail = BasicItem(
            context.getString(R.string.remember_email),
            p.first
        ).apply {
            addHead(CabinetAdapter.TYPE_CHECK)
        }
        loginList.add(remEmail)
        val remPass = BasicItem(
            context.getString(R.string.remember_password),
            p.second
        ).apply {
            addHead(CabinetAdapter.TYPE_CHECK)
        }
        loginList.add(remPass)
        val alterPath = BasicItem(
            context.getString(R.string.alter_path)
        ).apply {
            addHead(CabinetAdapter.TYPE_CHECK)
        }
        loginList.add(alterPath)
        loginList.add(BasicItem(context.getString(R.string.additionally) + ":").apply {
            addHead(CabinetAdapter.TYPE_TITLE)
        })
    }

    override suspend fun defaultState() {
        postState(CabinetState.Primary(screen, loginList))
    }

    override suspend fun doLoad() {
        when (val a = action) {
            is Action.Login ->
                doLogin(a.email, a.password)

            Action.Anketa ->
                loadAnketa(true)

            is Action.Word ->
                sendWord(a.index, a.word)

            Action.None -> {}
        }
        action = Action.None
    }

    fun login(email: String, password: String) {
        action = Action.Login(email, password)
        load()
    }

    fun getListWord() {
        action = Action.Anketa
        load()
    }

    fun selectWord(index: Int, word: String) {
        action = Action.Word(index, word)
        load()
    }

    fun loginScreen() {
        screen = CabinetScreen.LOGIN
        scope.launch {
            postState(CabinetState.Primary(screen, loginList))
        }
    }

    private fun cabinetScreen() {
        screen = CabinetScreen.CABINET
        scope.launch {
            postState(CabinetState.Primary(screen, cabinetList))
        }
    }

    private suspend fun doLogin(email: String, password: String) {
        helper.email = email
        var request = Request.Builder().head()
            .url(CabinetHelper.codingUrl(Urls.MainSite))
            .addHeader(NetConst.USER_AGENT, App.context.packageName)
            .build()
        val client = NeoClient.createHttpClient(true)
        var response = client.newCall(request).execute()
        var cookie = ""
        response.headers.forEach {
            if (it.second.contains("PHPSESSID"))
                cookie = it.second
        }
        response.close()
        if (cookie.isEmpty()) {
            postError(strings.authFailed)
            return
        }
        CabinetHelper.cookie = cookie
        val requestBody = FormBody.Builder()
            .add("user", email)
            .add("pass", password)
            .build()
        request = Request.Builder()
            .post(requestBody)
            .url(CabinetHelper.codingUrl(Urls.MainSite + "auth.php"))
            .addHeader(NetConst.USER_AGENT, App.context.packageName)
            .addHeader(NetConst.COOKIE, cookie)
            .build()
        response = client.newCall(request).execute()
        val br = BufferedReader(response.body.charStream(), 1000)
        val s = br.readLine()
        br.close()
        response.close()
        if (s.length == 2) //ok
            loadCabinet()
        else //INCORRECT_PASSWORD
            postError(s)
    }

    private suspend fun postError(msg: String) {
        postState(BasicState.Message(msg))
    }

    private suspend fun loadCabinet() {
        val s = loadAnketa(false)
        if (s.isEmpty()) {
            cabinetItem.title = strings.sendStatus
            cabinetItem.des = strings.selectStatus
        } else if (s.contains(ERROR_BOX)) {
            cabinetItem.title = strings.sendUnlivable
            cabinetItem.des = parseMessage(s)
        } else {
            cabinetItem.title = strings.selectedStatus
            cabinetItem.des = s
        }
        cabinetScreen()
    }

    private fun isCyrillic(response: Response): Boolean {
        response.headers.forEach {
            if (it.second.contains("charset"))
                return it.second.contains("1251")
        }
        return true
    }

    private suspend fun loadAnketa(loadWordList: Boolean): String {
        val request = Request.Builder()
            .url(CabinetHelper.codingUrl(Urls.MainSite + "edinenie/anketa.html"))
            .header(NetConst.USER_AGENT, App.context.packageName)
            .addHeader(NetConst.COOKIE, CabinetHelper.cookie)
            .build()
        val client = NeoClient.createHttpClient(true)
        val response = client.newCall(request).execute()
        if (response.isSuccessful.not()) throw NeoException.SiteCode(response.code)
        if (response.promisesBody().not()) throw NeoException.SiteNoResponse()
        val inStream = response.body.byteStream()
        val br = if (isCyrillic(response))
            BufferedReader(InputStreamReader(inStream, Const.ENCODING), 1000)
        else BufferedReader(InputStreamReader(inStream), 1000)
        var s = br.readLine()
        while (s != null) {
            if (s.contains(ERROR_BOX) || s.contains(WORDS_BOX)) //s.contains("fd_box") &
                break
            s = br.readLine()
        }
        br.close()
        response.close()

        when {
            s.isNullOrEmpty() ->
                postError(strings.profileFailed)

            s.contains(ERROR_BOX) -> // incorrect time s.contains("fd_box") ||
                return s

            s.contains(WORDS_BOX) -> {
                if (s.contains(SELECTED)) {
                    val i = s.indexOf(SELECTED) + SELECTED.length
                    return s.substring(i, s.indexOf("</", i))
                } else if (loadWordList) parseListWord(s)
            }

            else ->
                postError(strings.profileFailed)
        }
        return ""
    }

    private fun parseMessage(msg: String): String {
        var s = msg.replace(Const.BR, Const.N)
        s = if (s.contains(" ("))
            s.substring(0, s.indexOf(" ("))
        else
            s.substring(0, s.indexOf("</p"))
        s = s.substring(s.lastIndexOf(">") + 1)
        return s
    }

    private suspend fun parseListWord(words: String) {
        var s = words.substring(words.indexOf("-<") + 10, words.indexOf("</select>") - 9)
        s = s.replace("<option>", "")
        val m = s.split("</option>")
        for (i in m) wordList.add(BasicItem(i))
        screen = CabinetScreen.WORDS
        postState(CabinetState.Primary(screen, wordList))
    }

    private suspend fun sendWord(index: Int, word: String) {
        val requestBody = FormBody.Builder()
            .add("keyw", (index + 1).toString())
            .add("login", helper.email)
            .add("hash", "")
            .build()
        val request = Request.Builder()
            .post(requestBody)
            .url(CabinetHelper.codingUrl(Urls.MainSite + "savedata.php"))
            .header(NetConst.USER_AGENT, App.context.packageName)
            .addHeader(NetConst.COOKIE, CabinetHelper.cookie)
            .build()
        val client = NeoClient.createHttpClient(true)
        val response = client.newCall(request).execute()
        if (response.isSuccessful.not()) throw NeoException.SiteCode(response.code)
        if (response.promisesBody().not()) throw NeoException.SiteNoResponse()
        val inStream = response.body.byteStream()
        val br = BufferedReader(InputStreamReader(inStream, Const.ENCODING), 1000)
        val s = br.readLine()
        br.close()
        response.close()
        if (s == null) { //no error
            cabinetItem.title = strings.selectedStatus
            cabinetItem.des = word
            cabinetScreen()
        } else
            postError(s)
    }

    fun exit() {
        if (screen == CabinetScreen.CABINET) {
            helper.clear()
            loginScreen()
        } else {
            wordList.clear()
            cabinetScreen()
        }
    }

    fun forget(login: Boolean, password: Boolean) {
        if (login) {
            loginList[0].des = ""
            loginList[2].let {
                it.clear()
                it.addLink(CabinetAdapter.TYPE_CHECK, "")
            }
        }
        if (password) {
            loginList[1].des = ""
            loginList[3].let {
                it.clear()
                it.addLink(CabinetAdapter.TYPE_CHECK, "")
            }
        }
        helper.forget(login, password)
    }

    fun save(login: String, password: String) {
        loginList[0].des = login
        loginList[1].des = password
        loginList[2].let {
            it.clear()
            it.addLink(CabinetAdapter.TYPE_CHECK, login)
        }
        loginList[3].let {
            it.clear()
            it.addLink(CabinetAdapter.TYPE_CHECK, password)
        }
        helper.save(login, password)
    }

    fun setCheck(index: Int, value: Boolean) {
        loginList[index].let {
            it.clear()
            it.addLink(CabinetAdapter.TYPE_CHECK, if (value) "c" else "")
        }
    }

    fun setText(index: Int, value: String) {
        loginList[index].des = value
    }
}