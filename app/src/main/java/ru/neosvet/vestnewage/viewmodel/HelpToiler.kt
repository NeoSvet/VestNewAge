package ru.neosvet.vestnewage.viewmodel

import android.app.Activity
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.HelpItem
import ru.neosvet.vestnewage.view.activity.TipActivity
import ru.neosvet.vestnewage.view.activity.TipName
import ru.neosvet.vestnewage.viewmodel.basic.HelpStrings
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.HelpState
import ru.neosvet.vestnewage.viewmodel.state.ListState
import ru.neosvet.vestnewage.viewmodel.state.NeoState

class HelpToiler : ViewModel() {
    companion object {
        private const val FEEDBACK = 1
        private const val LINK_ON_GOOGLE = 2
        private const val LINK_ON_HUAWEI = 3
        private const val FEEDBACK_COUNT = 6
        private const val WRITE_TO_DEV = 1
        private const val TG_CHANNEL = 4
        private const val LINK_ON_SITE = 5
        private const val CHANGELOG = 6
        private const val TIPS = 3
        private const val TIPS_COUNT = 4
        private const val TIP_MAIN = 0
        private const val TIP_CALENDAR = 1
        private const val TIP_BROWSER = 2
        private const val TIP_SEARCH = 3
    }

    private val mstate = MutableLiveData<NeoState>()
    val state: LiveData<NeoState>
        get() = mstate
    private lateinit var strings: HelpStrings
    private var feedback = false
    private var tips = false
    private var verName = ""
    private val list = mutableListOf<HelpItem>()
    private val listFeedback = mutableListOf<HelpItem>()
    private val listTips = mutableListOf<HelpItem>()
    private val policyIndex: Int
        get() = FEEDBACK + if (feedback) FEEDBACK_COUNT + 1 else 1
    private val tipIndex: Int
        get() = if (feedback) FEEDBACK_COUNT + TIPS else TIPS

    fun init(act: Activity, section: Int) {
        verName = act.packageManager.getPackageInfo(act.packageName, 0).versionName
        strings = HelpStrings(
            srv_info = act.getString(R.string.srv_info),
            format_info = act.getString(R.string.format_info),
            feedback = act.resources.getStringArray(R.array.feedback),
            tips = act.resources.getStringArray(R.array.tips_sections)
        )
        val titles = act.resources.getStringArray(R.array.help_title)
        val contents = act.resources.getStringArray(R.array.help_content)
        for (i in titles.indices) {
            list.add(HelpItem(titles[i], contents[i]))
        }
        if (section > -1)
            list[section].opened = true
        restoreList()
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
                        strings.format_info, verName, App.version,
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
                R.drawable.little_star, R.drawable.ic_calendar,
                R.drawable.ic_menu, R.drawable.ic_search
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

    fun restoreList() {
        mstate.value = HelpState.Primary(list)
    }

    fun onItemClick(index: Int) {
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
            mstate.value = HelpState.Open(HelpState.Type.PRIVACY)
            return
        }
        list[index].opened = list[index].opened.not()

        mstate.value = ListState.Update(index, list[index])
    }

    private fun clickFeedback(index: Int) {
        when (index) {
            WRITE_TO_DEV -> {
                val msg = strings.srv_info + list[FEEDBACK + CHANGELOG].content
                mstate.value = BasicState.Message(msg)
            }

            TG_CHANNEL ->
                mstate.value = HelpState.Open(HelpState.Type.TELEGRAM)

            LINK_ON_SITE ->
                mstate.value = HelpState.Open(HelpState.Type.SITE)

            CHANGELOG ->
                mstate.value = HelpState.Open(HelpState.Type.CHANGELOG)


            LINK_ON_GOOGLE ->
                mstate.value = HelpState.Open(HelpState.Type.GOOGLE)

            LINK_ON_HUAWEI ->
                mstate.value = HelpState.Open(HelpState.Type.HUAWEI)
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
                TipActivity.showTip(TipName.MAIN_STAR)

            TIP_CALENDAR ->
                TipActivity.showTip(TipName.CALENDAR)

            TIP_BROWSER -> {
                TipActivity.showTip(TipName.BROWSER_FULLSCREEN)
                TipActivity.showTip(TipName.BROWSER_PANEL)
            }

            TIP_SEARCH ->
                TipActivity.showTip(TipName.SEARCH)
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