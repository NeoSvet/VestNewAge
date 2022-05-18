package ru.neosvet.vestnewage.model

import android.content.Context
import android.os.Build
import androidx.work.Data
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import ru.neosvet.utils.Const
import ru.neosvet.utils.NeoClient
import ru.neosvet.utils.UnsafeClient
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.helpers.CabinetHelper
import ru.neosvet.vestnewage.list.item.ListItem
import ru.neosvet.vestnewage.model.basic.CabinetStrings
import ru.neosvet.vestnewage.model.basic.MessageState
import ru.neosvet.vestnewage.model.basic.NeoViewModel
import ru.neosvet.vestnewage.model.basic.SuccessList
import java.io.BufferedReader
import java.io.InputStreamReader

class CabinetModel : NeoViewModel() {
    companion object {
        private const val ERROR_BOX = "lt_box"
        private const val WORDS_BOX = "name=\"keyw"
        private const val SELECTED = "selected>"
    }

    enum class Type {
        LOGIN, CABINET, WORDS
    }

    var type: Type = Type.LOGIN
        private set
    lateinit var helper: CabinetHelper
        private set
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
    }

    override fun onDestroy() {
    }

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, CabinetHelper.TAG)
        .putString(Const.MODE, type.toString())
        .putString(Const.EMAIL, helper.email)
        .build()

    fun login(email: String, password: String) {
        isRun = true
        scope.launch {
            helper.email = email
            doLogin(email, password)
            isRun = false
        }
    }

    fun getListWord() {
        isRun = true
        scope.launch {
            loadAnketa(true)
            isRun = false
        }
    }

    fun selectWord(index: Int, word: String) {
        isRun = true
        scope.launch {
            sendWord(index, word)
            isRun = false
        }
    }

    fun loginScreen() {
        type = Type.LOGIN
        mstate.postValue(SuccessList(loginList))
    }

    private fun cabinetScreen() {
        type = Type.CABINET
        mstate.postValue(SuccessList(cabinetList))
    }

    private fun doLogin(email: String, password: String) {
        var request: Request = Request.Builder()
            .url(NeoClient.CAB_SITE)
            .addHeader(NeoClient.USER_AGENT, App.context.packageName)
            .build()
        val client = createHttpClient()
        var response = client.newCall(request).execute()
        var cookie = response.header(NeoClient.SET_COOKIE)
        cookie = cookie!!.substring(0, cookie.indexOf(";"))
        CabinetHelper.cookie = cookie
        response.close()
        val requestBody: RequestBody = FormBody.Builder()
            .add("user", email)
            .add("pass", password)
            .build()
        request = Request.Builder()
            .post(requestBody)
            .url(NeoClient.CAB_SITE + "auth.php")
            .addHeader(NeoClient.USER_AGENT, App.context.packageName)
            .addHeader(NeoClient.COOKIE, cookie)
            .build()
        response = client.newCall(request).execute()
        val br = BufferedReader(response.body!!.charStream(), 1000)
        val s = br.readLine()
        br.close()
        response.close()
        if (s.length == 2) //ok
            loadCabinet()
        else //INCORRECT_PASSWORD
            postError(s)
    }

    private fun postError(msg: String) {
        mstate.postValue(MessageState(msg))
    }

    private fun loadCabinet() {
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

    private fun loadAnketa(loadWordList: Boolean): String {
        val builderRequest = Request.Builder()
        builderRequest.url(NeoClient.CAB_SITE + "edinenie/anketa.html")
        builderRequest.header(NeoClient.USER_AGENT, App.context.packageName)
        builderRequest.addHeader(NeoClient.COOKIE, CabinetHelper.cookie)
        val client = createHttpClient()
        val response = client.newCall(builderRequest.build()).execute()
        val br = BufferedReader(
            InputStreamReader(
                response.body!!.byteStream(), Const.ENCODING
            ), 1000
        )
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

    private fun parseListWord(words: String) {
        var s = words.substring(words.indexOf("-<") + 10, words.indexOf("</select>") - 9)
        s = s.replace("<option>", "")
        val m = s.split("</option>")
        wordList.clear()
        for (i in m)
            wordList.add(ListItem(i))
        type = Type.WORDS
        mstate.postValue(SuccessList(wordList))
    }

    private fun sendWord(index: Int, word: String) {
        val builderRequest = Request.Builder()
        builderRequest.url(NeoClient.CAB_SITE + "savedata.php")
        builderRequest.header(NeoClient.USER_AGENT, App.context.packageName)
        builderRequest.addHeader(NeoClient.COOKIE, CabinetHelper.cookie)
        val requestBody: RequestBody = FormBody.Builder()
            .add("keyw", (index + 1).toString())
            .add("login", helper.email)
            .add("hash", "")
            .build()
        builderRequest.post(requestBody)
        val client = createHttpClient()
        val response = client.newCall(builderRequest.build()).execute()
        val br = BufferedReader(
            InputStreamReader(
                response.body!!.byteStream(), Const.ENCODING
            ), 1000
        )
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
        when (type) {
            Type.LOGIN ->
                return true
            Type.CABINET ->
                exit()
            Type.WORDS -> {
                wordList.clear()
                cabinetScreen()
            }
        }
        return false
    }

    fun restoreScreen() {
        when (type) {
            Type.LOGIN ->
                mstate.postValue(SuccessList(loginList))
            Type.CABINET ->
                mstate.postValue(SuccessList(cabinetList))
            Type.WORDS ->
                mstate.postValue(SuccessList(wordList))
        }
    }

    private fun createHttpClient(): OkHttpClient =
        when(Build.VERSION.SDK_INT) {
            Build.VERSION_CODES.N ->
                UnsafeClient.createHttpClient2()
            Build.VERSION_CODES.M ->
                UnsafeClient.createHttpClient()
            else ->
                NeoClient.createHttpClient()
        }
}