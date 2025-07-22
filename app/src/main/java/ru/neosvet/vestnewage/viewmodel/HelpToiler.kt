package ru.neosvet.vestnewage.viewmodel

import android.app.Activity
import android.os.Build
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.HelpItem
import ru.neosvet.vestnewage.helper.DateHelper
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.TipUtils
import ru.neosvet.vestnewage.view.activity.TipActivity
import ru.neosvet.vestnewage.viewmodel.basic.HelpStrings
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.HelpState
import ru.neosvet.vestnewage.viewmodel.state.ListState
import ru.neosvet.vestnewage.viewmodel.state.NeoState

class HelpToiler : ViewModel() {
    companion object {
        // main index
        private const val BEGIN_BOOK = 1
        private const val FEEDBACK = 2
        private const val TIPS = 4

        // items count
        private const val FEEDBACK_COUNT = 6
        private const val TIPS_COUNT = 3

        // feedback index
        private const val WRITE_TO_DEV = 1
        private const val LINK_ON_GOOGLE = 2
        private const val LINK_ON_HUAWEI = 3
        private const val TG_CHANNEL = 4
        private const val LINK_ON_SITE = 5
        private const val CHANGELOG = 6

        // tip index
        private const val TIP_MAIN = 0
        private const val TIP_BROWSER = 1
        private const val TIP_SEARCH = 2
    }

    private val stateChannel = Channel<NeoState>()
    val state = stateChannel.receiveAsFlow()
    private lateinit var strings: HelpStrings
    private var feedback = false
    private var tips = false
    private var isInit = false
    private var verName = ""
    private val list = mutableListOf<HelpItem>()
    private val listFeedback = mutableListOf<HelpItem>()
    private val listTips = mutableListOf<HelpItem>()
    private val policyIndex: Int
        get() = FEEDBACK + if (feedback) FEEDBACK_COUNT + 1 else 1
    private val tipIndex: Int
        get() = if (feedback) FEEDBACK_COUNT + TIPS else TIPS

    fun start(act: Activity, section: Int) {
        if (!isInit) {
            verName = act.packageManager.getPackageInfo(act.packageName, 0).versionName ?: "3.x"
            strings = HelpStrings(
                srvInfo = act.getString(R.string.srv_info),
                formatInfo = act.getString(R.string.format_info),
                feedback = act.resources.getStringArray(R.array.feedback).toList(),
                tips = act.resources.getStringArray(R.array.tips_sections).toList()
            )
            val titles = act.resources.getStringArray(R.array.help_title)
            val contents = act.resources.getStringArray(R.array.help_content)
            for (i in titles.indices) {
                list.add(HelpItem(titles[i], contents[i]))
            }
            if (section > -1) list[section].opened = true
            isInit = true
        }
        restoreList()
    }

    private fun restoreList() {
        stateChannel.trySend(HelpState.Primary(list))
    }

    private fun getFeedback(): List<HelpItem> {
        if (listFeedback.isEmpty()) {
            val icons = arrayOf(
                R.drawable.gm,
                R.drawable.play_store,
                R.drawable.app_gallery,
                R.drawable.tg,
                R.drawable.www,
                0
            )
            for (i in icons.indices) {
                val item = if (icons[i] == 0) HelpItem(
                    title = strings.feedback[i],
                    opened = true,
                    content = String.format(
                        strings.formatInfo, verName, App.version,
                        Build.VERSION.RELEASE,
                        Build.VERSION.SDK_INT
                    )
                ) else HelpItem(
                    title = strings.feedback[i],
                    icon = icons[i]
                )
                listFeedback.add(item)
            }
        }

        return listFeedback
    }

    private fun getTips(): List<HelpItem> {
        if (listTips.isEmpty()) {
            val icons = arrayOf(
                R.drawable.icon, R.drawable.ic_menu, R.drawable.ic_search
            )
            for (i in icons.indices)
                listTips.add(
                    HelpItem(
                        title = strings.tips[i],
                        icon = icons[i]
                    )
                )
        }
        return listTips
    }

    fun onItemClick(index: Int) {
        if (index == BEGIN_BOOK) {
            DateHelper.setLoadedOtkr(true)
            stateChannel.trySend(HelpState.Open(HelpState.Type.BEGIN_BOOK))
            return
        }
        if (feedback && index > FEEDBACK) {
            if (index <= FEEDBACK + FEEDBACK_COUNT) {
                clickFeedback(index - FEEDBACK)
                return
            }
        }
        if (index == FEEDBACK) {
            switchFeedback()
            return
        }
        val i = tipIndex
        if (tips && index > i) {
            if (index <= i + TIPS_COUNT) {
                clickTip(index - i - 1)
                return
            }
        }
        if (index == i) {
            switchTips()
            return
        }
        if (index == policyIndex) {
            stateChannel.trySend(HelpState.Open(HelpState.Type.PRIVACY))
            return
        }
        list[index].opened = list[index].opened.not()

        stateChannel.trySend(ListState.Update(index, list[index]))
    }

    private fun clickFeedback(index: Int) {
        when (index) {
            WRITE_TO_DEV -> {
                val msg = strings.srvInfo + Const.CRLF + list[FEEDBACK + CHANGELOG].content
                stateChannel.trySend(BasicState.Message(Const.CRLF + Const.CRLF + msg))
            }

            TG_CHANNEL ->
                stateChannel.trySend(HelpState.Open(HelpState.Type.TELEGRAM))

            LINK_ON_SITE ->
                stateChannel.trySend(HelpState.Open(HelpState.Type.SITE))

            CHANGELOG ->
                stateChannel.trySend(HelpState.Open(HelpState.Type.CHANGELOG))


            LINK_ON_GOOGLE ->
                stateChannel.trySend(HelpState.Open(HelpState.Type.GOOGLE))

            LINK_ON_HUAWEI ->
                stateChannel.trySend(HelpState.Open(HelpState.Type.HUAWEI))
        }
    }

    private fun switchFeedback() {
        feedback = feedback.not()
        if (feedback) {
            list[FEEDBACK].opened = true
            var i = 1
            getFeedback().forEach {
                list.add(FEEDBACK + i, it)
                i++
            }
        } else {
            list[FEEDBACK].opened = false
            for (i in 0 until FEEDBACK_COUNT)
                list.removeAt(FEEDBACK + 1)
        }
        restoreList()
    }

    private fun clickTip(index: Int) {
        when (index) {
            TIP_MAIN ->
                TipActivity.showTip(TipUtils.Type.MAIN)

            TIP_BROWSER ->
                TipActivity.showTip(TipUtils.Type.BROWSER)

            TIP_SEARCH ->
                TipActivity.showTip(TipUtils.Type.SEARCH)
        }
    }

    private fun switchTips() {
        tips = tips.not()
        val index = tipIndex
        if (tips) {
            list[index].opened = true
            var i = 1
            getTips().forEach {
                list.add(index + i, it)
                i++
            }
        } else {
            list[index].opened = false
            for (i in 0 until TIPS_COUNT)
                list.removeAt(index + 1)
        }
        restoreList()
    }
}