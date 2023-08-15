package ru.neosvet.vestnewage.viewmodel

import android.content.Context
import android.os.Build
import androidx.work.Data
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.internal.http.promisesBody
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.data.NeoException
import ru.neosvet.vestnewage.helper.CabinetHelper
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.NetConst
import ru.neosvet.vestnewage.network.UnsafeClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.viewmodel.basic.CabinetStrings
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import java.io.BufferedReader
import java.io.InputStreamReader

class CabinetToiler : NeoToiler() {
    companion object {
        private const val ERROR_BOX = "lt_box"
        private const val WORDS_BOX = "name=\"keyw"
        private const val SELECTED = "selected>"
    }

    sealed class Action {
        data class Login(val email: String, val password: String) : Action()
        data class Word(val index: Int, val word: String) : Action()
        object Anketa : Action()
        object None : Action()
    }

    enum class Screen {
        LOGIN, CABINET, WORDS
    }

    var isReadyLogin = false
        private set
    var screen: Screen = Screen.LOGIN
        private set
    lateinit var helper: CabinetHelper
        private set
    private var action: Action = Action.None
    private lateinit var strings: CabinetStrings
    private val wordList: MutableList<ListItem> by lazy { mutableListOf() }
    private val loginList = mutableListOf<ListItem>()
    private val cabinetList = mutableListOf<ListItem>()
    private val cabinetItem = ListItem("")
    private var isInit = false

    fun init(context: Context) {
        if (isInit) return
        isInit = true
        helper = CabinetHelper(context)
        strings = CabinetStrings(
            selected_status = context.getString(R.string.selected_status),
            anketa_failed = context.getString(R.string.anketa_failed),
            send_status = context.getString(R.string.send_status),
            select_status = context.getString(R.string.select_status),
            send_unlivable = context.getString(R.string.send_unlivable)
        )

        val m = context.resources.getStringArray(R.array.cabinet_main)
        var i = 0
        while (i < m.size) {
            loginList.add(ListItem(m[i]).apply {
                des = m[i + 1]
            })
            i += 2
        }
        cabinetList.add(cabinetItem)
        for (s in context.resources.getStringArray(R.array.cabinet_enter))
            cabinetList.add(ListItem(s))
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

    override fun getInputData(): Data {
        val m = if (action is Action.Login)
            "Login" else action.toString()
        return Data.Builder()
            .putString(Const.TASK, CabinetHelper.TAG)
            .putString(Const.LIST, screen.toString())
            .putString(Const.MODE, m)
            .build()
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
        screen = Screen.LOGIN
        setState(NeoState.ListValue(loginList))
    }

    private fun cabinetScreen() {
        screen = Screen.CABINET
        setState(NeoState.ListValue(cabinetList))
    }

    private suspend fun doLogin(email: String, password: String) {
        helper.email = email
        var request: Request = Request.Builder()
            .url(Urls.MainSite)
            .addHeader(NetConst.USER_AGENT, App.context.packageName)
            .build()
        val client = createHttpClient()
        var response = client.newCall(request).execute()
        var cookie = response.header(NetConst.SET_COOKIE)
        cookie = cookie!!.substring(0, cookie.indexOf(";"))
        CabinetHelper.cookie = cookie
        response.close()
        val requestBody: RequestBody = FormBody.Builder()
            .add("user", email)
            .add("pass", password)
            .build()
        request = Request.Builder()
            .post(requestBody)
            .url(Urls.MainSite + "auth.php")
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
        postState(NeoState.Message(msg))
    }

    private suspend fun loadCabinet() {
        val s = loadAnketa(false)
        if (s.isEmpty()) {
            cabinetItem.title = strings.send_status
            cabinetItem.des = strings.select_status
        } else if (s.contains(ERROR_BOX)) {
            cabinetItem.title = strings.send_unlivable
            cabinetItem.des = parseMessage(s)
        } else {
            cabinetItem.title = strings.selected_status
            cabinetItem.des = s
        }
        cabinetScreen()
    }

    private suspend fun loadAnketa(loadWordList: Boolean): String {
        val builderRequest = Request.Builder()
        builderRequest.url(Urls.MainSite + "edinenie/anketa.html")
        builderRequest.header(NetConst.USER_AGENT, App.context.packageName)
        builderRequest.addHeader(NetConst.COOKIE, CabinetHelper.cookie)
        val client = createHttpClient()
        val response = client.newCall(builderRequest.build()).execute()
        if (response.isSuccessful.not()) throw NeoException.SiteCode(response.code)
        if (response.promisesBody().not()) throw NeoException.SiteNoResponse()
        val inStream = response.body.byteStream()
        val br = BufferedReader(InputStreamReader(inStream, Const.ENCODING), 1000)
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
                postError(strings.anketa_failed)
            s.contains(ERROR_BOX) -> // incorrect time s.contains("fd_box") ||
                return s
            s.contains(WORDS_BOX) -> {
                if (s.contains(SELECTED)) {
                    val i = s.indexOf(SELECTED) + SELECTED.length
                    return s.substring(i, s.indexOf("</", i))
                } else if (loadWordList) parseListWord(s)
            }
            else ->
                postError(strings.anketa_failed)
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
        wordList.clear()
        for (i in m)
            wordList.add(ListItem(i))
        screen = Screen.WORDS
        postState(NeoState.ListValue(wordList))
    }

    private suspend fun sendWord(index: Int, word: String) {
        val builderRequest = Request.Builder()
        builderRequest.url(Urls.MainSite + "savedata.php")
        builderRequest.header(NetConst.USER_AGENT, App.context.packageName)
        builderRequest.addHeader(NetConst.COOKIE, CabinetHelper.cookie)
        val requestBody: RequestBody = FormBody.Builder()
            .add("keyw", (index + 1).toString())
            .add("login", helper.email)
            .add("hash", "")
            .build()
        builderRequest.post(requestBody)
        val client = createHttpClient()
        val response = client.newCall(builderRequest.build()).execute()
        if (response.isSuccessful.not()) throw NeoException.SiteCode(response.code)
        if (response.promisesBody().not()) throw NeoException.SiteNoResponse()
        val inStream = response.body.byteStream()
        val br = BufferedReader(InputStreamReader(inStream, Const.ENCODING), 1000)
        val s = br.readLine()
        br.close()
        response.close()
        if (s == null) { //no error
            cabinetItem.title = strings.selected_status
            cabinetItem.des = word
            cabinetScreen()
        } else
            postError(s)
    }

    fun exit() {
        helper.clear()
        loginScreen()
    }

    fun onBack(): Boolean {
        when (screen) {
            Screen.LOGIN ->
                return true
            Screen.CABINET ->
                exit()
            Screen.WORDS -> {
                wordList.clear()
                cabinetScreen()
            }
        }
        return false
    }

//    fun restoreScreen() {
//        when (screen) {
//            Screen.LOGIN ->
//                setState( NeoState.ListValue(loginList))
//            Screen.CABINET ->
//                setState( NeoState.ListValue(cabinetList))
//            Screen.WORDS ->
//                setState( NeoState.ListValue(wordList))
//        }
//    }

    private fun createHttpClient(): OkHttpClient =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            NeoClient.createHttpClient()
        else
            UnsafeClient.createHttpClient()

    fun check(email: String, password: String) {
        isReadyLogin = if (email.length > 5 && password.length > 5) {
            email.contains("@") && email.contains(".")
        } else false
    }
}