package ru.neosvet.vestnewage.viewmodel

import android.app.Activity
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.HelpItem
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.view.list.HelpAdapter
import ru.neosvet.vestnewage.viewmodel.basic.HelpStrings
import ru.neosvet.vestnewage.viewmodel.basic.ListEvent
import ru.neosvet.vestnewage.viewmodel.basic.NeoState

class HelpToiler : ViewModel(), HelpAdapter.ItemClicker {
    companion object {
        private const val FEEDBACK = 1
        private const val FEEDBACK_COUNT = 5
        private const val WRITE_TO_DEV = 1
        private const val LINK_ON_APP = 2
        private const val TG_CHANNEL = 3
        private const val LINK_ON_SITE = 4
        private const val CHANGELOG = 5
    }

    private val mstate = MutableLiveData<NeoState>()
    val state: LiveData<NeoState>
        get() = mstate
    private lateinit var strings: HelpStrings
    private var feedback = false
    private var verCode = 0
    private var verName = ""
    val list = mutableListOf<HelpItem>()
    private val policyIndex: Int
        get() = FEEDBACK + if (feedback) FEEDBACK_COUNT + 1 else 1

    fun init(act: Activity, section: Int) {
        val titles = act.resources.getStringArray(R.array.help_title)
        val contents = act.resources.getStringArray(R.array.help_content)
        for (i in titles.indices) {
            list.add(HelpItem(titles[i], contents[i]))
        }
        if (section > -1)
            list[section].opened = true
        restore()

        verName = act.packageManager.getPackageInfo(act.packageName, 0).versionName
        verCode = act.packageManager.getPackageInfo(act.packageName, 0).versionCode
        strings = HelpStrings(
            srv_info = act.getString(R.string.srv_info),
            write_to_dev = act.getString(R.string.write_to_dev),
            link_on_app = act.getString(R.string.link_on_app),
            tg_channel = act.getString(R.string.tg_channel),
            page_app = act.getString(R.string.page_app),
            changelog = act.getString(R.string.changelog),
            format_info = act.getString(R.string.format_info)
        )
    }

    fun restore() {
        mstate.value = NeoState.ListState(ListEvent.RELOAD)
    }

    override fun onItemClick(index: Int) {
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
        if (index == policyIndex) {
            mstate.value = NeoState.ListState(ListEvent.MOVE, index)
            return
        }
        list[index].opened = list[index].opened.not()
        mstate.value = NeoState.ListState(ListEvent.CHANGE, index)
    }

    private fun clickFeedback(index: Int) {
        when (index) {
            WRITE_TO_DEV -> {
                val msg = strings.srv_info + list[FEEDBACK + CHANGELOG].content
                Lib.openInApps(Const.mailto + msg, null)
            }
            LINK_ON_APP ->
                mstate.value = NeoState.ListState(ListEvent.REMOTE)
            TG_CHANNEL ->
                Lib.openInApps("https://t.me/+nUS5nlrZsvM3MTEy", null)
            LINK_ON_SITE ->
                Lib.openInApps("http://neosvet.ucoz.ru/vna/", null)
            CHANGELOG ->
                Lib.openInApps("http://neosvet.ucoz.ru/vna/changelog.html", null)
        }
    }

    private fun switchFeedback() {
        feedback = feedback.not()
        if (feedback) {
            list[FEEDBACK].opened = true
            list.add(
                FEEDBACK + WRITE_TO_DEV,
                HelpItem(
                    title = strings.write_to_dev,
                    icon = R.drawable.gm
                )
            )
            list.add(
                FEEDBACK + LINK_ON_APP,
                HelpItem(
                    title = strings.link_on_app,
                    icon = R.drawable.play_store
                )
            )
            list.add(
                FEEDBACK + TG_CHANNEL,
                HelpItem(
                    title = strings.tg_channel,
                    icon = R.drawable.tg
                )
            )
            list.add(
                FEEDBACK + LINK_ON_SITE,
                HelpItem(
                    title = strings.page_app,
                    icon = R.drawable.www
                )
            )
            val item = HelpItem(
                title = strings.changelog,
                opened = true,
                content = String.format(
                    strings.format_info, verName, verCode,
                    Build.VERSION.RELEASE,
                    Build.VERSION.SDK_INT
                )
            )
            list.add(FEEDBACK + CHANGELOG, item)
        } else {
            list[FEEDBACK].opened = false
            for (i in 0 until FEEDBACK_COUNT)
                list.removeAt(FEEDBACK + 1)
        }
        restore()
    }
}