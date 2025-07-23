package ru.neosvet.vestnewage.view.fragment

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.CheckItem
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.Section
import ru.neosvet.vestnewage.data.SettingsItem
import ru.neosvet.vestnewage.databinding.SettingsFragmentBinding
import ru.neosvet.vestnewage.helper.DateHelper
import ru.neosvet.vestnewage.helper.MainHelper
import ru.neosvet.vestnewage.helper.SummaryHelper
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.service.CheckWorker
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.NotificationUtils
import ru.neosvet.vestnewage.utils.PromUtils
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.MainActivity
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.dialog.MessageDialog
import ru.neosvet.vestnewage.view.dialog.SetNotifDialog
import ru.neosvet.vestnewage.view.list.CheckAdapter
import ru.neosvet.vestnewage.view.list.SettingsAdapter
import ru.neosvet.vestnewage.viewmodel.SettingsToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.NeoState
import ru.neosvet.vestnewage.viewmodel.state.SettingsState

class SettingsFragment : NeoFragment() {
    companion object {
        private const val CHECK_MAX = 7
        private const val PROM_MAX = 60
    }

    private var binding: SettingsFragmentBinding? = null
    private var dialog: SetNotifDialog? = null
    private val prefMain: SharedPreferences by lazy {
        requireContext().getSharedPreferences(
            MainHelper.TAG, Context.MODE_PRIVATE
        )
    }
    private val prefSummary: SharedPreferences by lazy {
        requireContext().getSharedPreferences(
            SummaryHelper.TAG, Context.MODE_PRIVATE
        )
    }
    private val prefProm: SharedPreferences by lazy {
        requireContext().getSharedPreferences(
            PromUtils.TAG, Context.MODE_PRIVATE
        )
    }
    private val toiler: SettingsToiler
        get() = neotoiler as SettingsToiler
    private val anRotate: Animation by lazy {
        initAnimation()
    }
    private var stopRotate = false
    private val adapter = SettingsAdapter(ScreenUtils.isWide)
    private lateinit var adapterCheck: CheckAdapter
    private lateinit var adapterProm: CheckAdapter

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this)[SettingsToiler::class.java]

    override val title: String
        get() = getString(R.string.settings)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = SettingsFragmentBinding.inflate(inflater, container, false).also {
        binding = it
    }.root

    override fun onViewCreated(savedInstanceState: Bundle?) {
        initBaseSection()
        initScreenSection()
        initCheckSection()
        initPromSection()
        initClearSection()
        initDefaultSection()
        initMessageSection()
        binding?.run {
            rvSettings.layoutManager = GridLayoutManager(
                requireContext(),
                if (ScreenUtils.isWide) 2 else 1
            )
            rvSettings.adapter = adapter
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        toiler.setStatus(
            SettingsState.Status(
                listVisible = adapter.visible,
                alarmVisible = binding?.pAlarm?.isVisible == true
            )
        )
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onChangedInsets(insets: android.graphics.Insets) {
        binding?.run {
            rvSettings.updatePadding(bottom = App.CONTENT_BOTTOM_INDENT)
            pAlarm.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = App.CONTENT_BOTTOM_INDENT
            }
        }
    }

    override fun onChangedOtherState(state: NeoState) {
        when (state) {

            is SettingsState.Status ->
                restoreStatus(state)

            is BasicState.Message -> {
                setStatus(false)
                act?.showToast(String.format(getString(R.string.format_freed_size), state.message))
                neotoiler.clearStates()
            }
        }
    }

    private fun restoreStatus(state: SettingsState.Status) {
        binding?.pAlarm?.isVisible = state.alarmVisible
        adapter.setVisible(state.listVisible)
    }

    override fun setStatus(load: Boolean) {
        if (load) {
            binding?.run {
                stopRotate = false
                pClear.isVisible = true
                ivClear.startAnimation(anRotate)
            }
        } else stopRotate = true
    }

    private fun initAnimation(): Animation {
        val animation = AnimationUtils.loadAnimation(requireContext(), R.anim.rotate)
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                binding?.run {
                    if (stopRotate)
                        pClear.isVisible = false
                    else
                        ivClear.startAnimation(anRotate)
                }
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
        return animation
    }

    private fun initBaseSection() {
        val list = mutableListOf<CheckItem>()
        list.add(
            CheckItem(
                title = getString(R.string.always_dark_theme),
                isChecked = prefMain.getBoolean(Const.ALWAYS_DARK, false)
            )
        )
        list.add(
            CheckItem(
                title = getString(R.string.float_prom_time),
                isChecked = prefMain.getBoolean(Const.PROM_FLOAT, false)
            )
        )
        list.add(
            CheckItem(
                title = getString(R.string.start_with_new),
                isChecked = prefMain.getBoolean(Const.START_NEW, false)
            )
        )
        list.add(
            CheckItem(
                title = getString(R.string.use_site_com),
                isChecked = Urls.isSiteCom
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) list.add(
            CheckItem(
                title = getString(R.string.certificate_ignore),
                isChecked = App.unsafeClient
            )
        )
        adapter.addItem(
            SettingsItem.CheckList(
                title = getString(R.string.base),
                isSingleSelect = false,
                list = list,
                onChecked = { index, checked ->
                    if (index == 3) {
                        Urls.setCom(checked)
                        return@CheckList
                    }
                    if (index == 4) {
                        if (checked) App.needUnsafeClient()
                        else App.offUnsafeClient()
                        return@CheckList
                    }
                    val name = when (index) {
                        0 -> {
                            act?.setDarkTheme(checked)
                            Const.ALWAYS_DARK
                        }

                        1 -> {
                            act?.setFloatProm(checked)
                            Const.PROM_FLOAT
                        }

                        else ->
                            Const.START_NEW
                    }
                    prefMain.edit {
                        putBoolean(name, checked)
                    }
                }
            ))
    }

    private fun initScreenSection() {
        val list = mutableListOf<CheckItem>()
        var screen = prefMain.getInt(Const.START_SCEEN, Section.HOME.value)
        if (ScreenUtils.isTablet)
            screen--
        else {
            list.add(
                CheckItem(title = getString(R.string.menu), isChecked = screen == 0)
            )
        }
        list.add(
            CheckItem(
                title = getString(R.string.home_screen),
                isChecked = screen == list.size
            )
        )
        list.add(
            CheckItem(
                title = getString(R.string.calendar),
                isChecked = screen == list.size
            )
        )
        list.add(
            CheckItem(
                title = getString(R.string.summary),
                isChecked = screen == list.size
            )
        )
        adapter.addItem(
            SettingsItem.CheckList(
                title = getString(R.string.start_screen),
                isSingleSelect = true,
                list = list,
                onChecked = { index, _ ->
                    val i = if (ScreenUtils.isTablet) index + 1 else index
                    setStartScreen(i)
                }
            ))
    }

    private fun initClearSection() {
        val list = mutableListOf<CheckItem>()
        list.add(CheckItem(getString(R.string.cache), SettingsToiler.CLEAR_CACHE))
        if (DateHelper.isLoadedOtkr())
            list.add(
                CheckItem(
                    getString(R.string.format_book_years).format(2004, 2015),
                    SettingsToiler.CLEAR_OLD_BOOK
                )
            )
        val year = DateUnit.initToday().year
        list.add(
            CheckItem(
                getString(R.string.format_book_years).format(2016, year - 2),
                SettingsToiler.CLEAR_NEW_BOOK
            )
        )
        list.add(
            CheckItem(
                getString(R.string.format_book_years).format(year - 1, year),
                SettingsToiler.CLEAR_NOW_BOOK
            )
        )
        list.add(
            CheckItem(
                getString(R.string.other_books),
                SettingsToiler.CLEAR_OTHER_BOOKS
            )
        )
        list.add(
            CheckItem(
                getString(R.string.articles_and_summary),
                SettingsToiler.CLEAR_ARTICLES
            )
        )
        list.add(CheckItem(getString(R.string.markers), SettingsToiler.CLEAR_MARKERS))

        adapter.addItem(
            SettingsItem.CheckListButton(
                title = getString(R.string.free_storage),
                list = list,
                buttonLabel = getString(R.string.delete),
                onClick = {
                    if (!isBlocked) {
                        setStatus(true)
                        toiler.startClear(it)
                    }
                }
            )
        )
    }

    private fun initCheckSection() {
        var v = prefSummary.getInt(Const.TIME, Const.TURN_OFF)
        if (v == Const.TURN_OFF)
            v = CHECK_MAX
        else {
            v /= 15
            if (v > 3) v = v / 4 + 2 else v--
        }
        val list = initCheckList(
            listOf(
                getString(R.string.check_also), getString(R.string.additionally),
                getString(R.string.doctrine), getString(R.string.academy)
            ), listOf(
                false, prefSummary.getBoolean(Const.MODE, true),
                prefSummary.getBoolean(Const.DOCTRINE, false),
                prefSummary.getBoolean(Const.PLACE, false)
            )
        )
        adapterCheck = CheckAdapter(
            list = list, checkByBg = true,
            zeroMargin = true, onChecked = this::onCheckItem
        )

        adapter.addItem(
            SettingsItem.Notification(
                title = getString(R.string.notif_new),
                offLabel = getString(R.string.less),
                onLabel = getString(R.string.often),
                listAdapter = adapterCheck,
                valueSeek = v,
                maxSeek = CHECK_MAX,
                changeValue = this::setCheckTime,
                fixValue = this::saveCheck,
                onClick = {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        dialog = SetNotifDialog(requireActivity(), SummaryHelper.TAG)
                        dialog?.show()
                    } else {
                        val intent =
                            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, act!!.packageName)
                                .putExtra(
                                    Settings.EXTRA_CHANNEL_ID,
                                    NotificationUtils.CHANNEL_SUMMARY
                                )
                        startActivity(intent)
                    }
                }
            ))
    }

    private fun initCheckList(
        label: List<String>,
        value: List<Boolean>
    ): List<CheckItem> {
        val list = mutableListOf<CheckItem>()
        for (i in label.indices) {
            val item = CheckItem(label[i], i)
            if (value.size > i && value[i])
                item.isChecked = true
            list.add(item)
        }
        return list
    }

    private fun onCheckItem(index: Int, check: Boolean): Int {
        val name = when (index) {
            0 -> return 0
            1 -> Const.MODE
            2 -> Const.DOCTRINE
            else -> Const.PLACE //3
        }
        prefSummary.edit {
            putBoolean(name, check)
        }
        return CheckAdapter.ACTION_NONE
    }

    private fun onPromItem(index: Int, check: Boolean): Int {
        when (index) {
            1 -> binding?.pAlarm?.isVisible = true
            2 -> toiler.openAlarm()
        }
        return index
    }

    private fun initPromSection() {
        var v = prefProm.getInt(Const.TIME, Const.TURN_OFF)
        if (v == Const.TURN_OFF)
            v = PROM_MAX

        val list = initCheckList(
            listOf(
                getString(R.string.alarm),
                getString(R.string.set),
                getString(R.string.look)
            ), listOf()
        )
        adapterProm = CheckAdapter(
            list = list, checkByBg = true,
            zeroMargin = true, onChecked = this::onPromItem
        )

        adapter.addItem(
            SettingsItem.Notification(
                title = getString(R.string.notif_prom),
                offLabel = getString(R.string.advance),
                onLabel = getString(R.string.prom),
                listAdapter = adapterProm,
                valueSeek = v,
                maxSeek = PROM_MAX,
                changeValue = this::setPromTime,
                fixValue = this::saveProm,
                onClick = {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        dialog = SetNotifDialog(requireActivity(), PromUtils.TAG)
                        dialog?.show()
                    } else {
                        val intent =
                            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, act!!.packageName)
                                .putExtra(
                                    Settings.EXTRA_CHANNEL_ID,
                                    NotificationUtils.CHANNEL_PROM
                                )
                        startActivity(intent)
                    }
                }
            ))

        binding?.run {
            btnAlarmTo3Hours.setOnClickListener(this@SettingsFragment::clickAlarm)
            btnAlarmTo11Hours.setOnClickListener(this@SettingsFragment::clickAlarm)
            btnAlarmTo19Hours.setOnClickListener(this@SettingsFragment::clickAlarm)
            btnClose.setOnClickListener {
                pAlarm.isVisible = false
            }
        }
    }

    private fun clickAlarm(v: View) {
        val h = when (v.id) {
            R.id.btn_alarm_to_3_hours -> 3
            R.id.btn_alarm_to_11_hours -> 11
            else -> 19 //R.id.btn_alarm_to_19_hours
        }
        toiler.setAlarm(h, prefProm.getInt(Const.TIME, -1))
    }

    private fun initDefaultSection() {
        val pack = "package:${requireContext().packageName}".toUri()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            adapter.addItem(
                SettingsItem.Message(
                    title = getString(R.string.about_default),
                    text = getString(R.string.info_default) + Const.N
                            + getString(R.string.info_default_a),
                    buttonLabel = getString(R.string.set_),
                    onClick = {
                        val intent =
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pack)
                        startActivity(intent)
                    }
                ))
            return
        }

        adapter.addItem(
            SettingsItem.Message(
                title = getString(R.string.about_default),
                text = getString(R.string.info_default) + Const.N
                        + getString(R.string.info_default_b),
                buttonLabel = getString(R.string.set_),
                onClick = {
                    val intent =
                        Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, pack)
                    startActivity(intent)
                }
            ))
    }

    private fun initMessageSection() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            adapter.addItem(
                SettingsItem.Message(
                    title = getString(R.string.about_manager),
                    text = getString(R.string.info_manager),
                    buttonLabel = "",
                    onClick = {}
                ))
            return
        }

        adapter.addItem(
            SettingsItem.Message(
                title = getString(R.string.about_manager),
                text = getString(R.string.info_manager) + Const.NN
                        + getString(R.string.info_battery),
                buttonLabel = getString(R.string.set_battery),
                onClick = {
                    val intent =
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                }
            ))
    }

    private fun setStartScreen(value: Int) {
        prefMain.edit {
            putInt(Const.START_SCEEN, value)
        }
        val main = Intent(act, MainActivity::class.java)
        main.putExtra(Const.START_SCEEN, false)
        startActivity(main)
        act?.finish()
    }

    private fun saveCheck(value: Int) {
        val editor = prefSummary.edit()
        var v = value
        if (v < CHECK_MAX) {
            if (v > 2) v = (v - 2) * 4 else v++
            v *= 15
        } else v = Const.TURN_OFF
        editor.putInt(Const.TIME, v)
        editor.apply()
        CheckWorker.set(v)
    }

    private fun saveProm(value: Int) {
        prefProm.edit {
            putInt(Const.TIME, if (value == PROM_MAX) Const.TURN_OFF else value)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            setAlarmNew(value)
        else setAlarm(value)
    }

    private fun setAlarm(value: Int) {
        val prom = PromUtils(null)
        prom.initNotif(value)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun setAlarmNew(value: Int) {
        val manager =
            ContextCompat.getSystemService(requireContext(), AlarmManager::class.java)
        if (manager?.canScheduleExactAlarms() == false) {
            val alert = MessageDialog(requireActivity()).apply {
                setTitle(getString(R.string.notif_prom))
                setMessage(getString(R.string.how_give_permission_alarm))
                setRightButton(getString(android.R.string.ok)) {
                    Intent().also {
                        it.action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                        startActivity(it)
                    }
                    dismiss()
                }
            }
            alert.show(null)
        } else setAlarm(value)
    }

    private fun setCheckTime(label: TextView, value: Int) {
        val t = StringBuilder(getString(R.string.check_summary))
        t.append(" ")
        if (value == CHECK_MAX) {
            t.append(getString(R.string.turn_off))
            label.text = t
            return
        }
        var v = value + 1
        var bH = false
        if (v > 3) {
            bH = true
            v -= 3
        } else v *= 15
        if (v == 1)
            t.append(getString(R.string.each_one))
        else {
            t.append(getString(R.string.each_more))
            t.append(" ")
            t.append(v)
            t.append(" ")
            if (bH) t.append(getString(R.string.hours))
            else t.append(getString(R.string.minutes))
        }
        label.text = t
    }

    private fun setPromTime(label: TextView, value: Int) {
        var v = value
        if (v == PROM_MAX) {
            label.text = getString(R.string.prom_notif_off)
            return
        }
        val t = StringBuilder(getString(R.string.prom_notif))
        t.append(" ")
        t.append(getString(R.string.`in`))
        t.append(" ")
        v++
        when (v) {
            1 ->
                t.append(resources.getStringArray(R.array.time)[3])

            in 5..20 -> {
                t.append(v)
                t.append(" ")
                t.append(resources.getStringArray(R.array.time)[4])
            }

            else -> {
                t.append(v)
                t.append(" ")
                when (v % 10) {
                    1 -> t.append(resources.getStringArray(R.array.time)[3])
                    in 2..4 -> t.append(resources.getStringArray(R.array.time)[5])
                    else -> t.append(resources.getStringArray(R.array.time)[4])
                }
            }
        }
        label.text = t
    }

    fun putRingtone(data: Intent?) {
        data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            ?.let { uri ->
                val ringTone = RingtoneManager.getRingtone(act, uri)
                dialog?.putRingtone(ringTone.getTitle(act), uri.toString())
            }
    }
}