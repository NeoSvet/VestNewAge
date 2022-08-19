package ru.neosvet.vestnewage.view.fragment

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.CheckItem
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.SettingsItem
import ru.neosvet.vestnewage.databinding.SettingsFragmentBinding
import ru.neosvet.vestnewage.helper.DateHelper
import ru.neosvet.vestnewage.helper.MainHelper
import ru.neosvet.vestnewage.helper.SummaryHelper
import ru.neosvet.vestnewage.service.CheckStarter
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.NotificationUtils
import ru.neosvet.vestnewage.utils.PromUtils
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.MainActivity
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.dialog.SetNotifDialog
import ru.neosvet.vestnewage.view.list.SettingsAdapter
import ru.neosvet.vestnewage.viewmodel.SettingsToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler

class SettingsFragment : NeoFragment() {
    companion object {
        private const val CHECK_MAX = 7
        private const val PROM_MAX = 60
    }

    private var binding: SettingsFragmentBinding? = null
    private var dialog: SetNotifDialog? = null
    private val prefMain: SharedPreferences by lazy {
        requireContext().getSharedPreferences(MainHelper.TAG, Context.MODE_PRIVATE)
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
    private val adapter: SettingsAdapter by lazy {
        SettingsAdapter(toiler.panels, ScreenUtils.isWide)
    }

    override fun initViewModel(): NeoToiler {
        return ViewModelProvider(this).get(SettingsToiler::class.java)
    }

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
        initMessageSection()
        binding?.run {
            rvSettings.layoutManager = GridLayoutManager(
                requireContext(),
                if (ScreenUtils.isWide) 2 else 1
            )
            rvSettings.adapter = adapter
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onChangedOtherState(state: NeoState) {
        if (state is NeoState.LongValue) {
            setStatus(false)
            val size = state.value / 1048576f //to MegaByte
            act?.showToast(String.format(getString(R.string.format_freed_size), size))
            neotoiler.clearLongValue()
        }
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
        adapter.addItem(SettingsItem.CheckList(
            title = getString(R.string.base),
            isSingleSelect = false,
            list = list,
            onChecked = { index, checked ->
                val name = if (index == 0) {
                    act?.setFloatProm(checked)
                    Const.PROM_FLOAT
                } else
                    Const.START_NEW
                val editor = prefMain.edit()
                editor.putBoolean(name, checked)
                editor.apply()
            }
        ))
    }

    private fun initScreenSection() {
        val list = mutableListOf<CheckItem>()
        var screen = prefMain.getInt(Const.START_SCEEN, Const.SCREEN_CALENDAR)
        if (ScreenUtils.isTablet)
            screen--
        else {
            list.add(
                CheckItem(title = getString(R.string.menu), isChecked = screen == list.size)
            )
        }
        list.add(
            CheckItem(title = getString(R.string.calendar), isChecked = screen == list.size)
        )
        list.add(
            CheckItem(title = getString(R.string.summary), isChecked = screen == list.size)
        )
        adapter.addItem(SettingsItem.CheckList(
            title = getString(R.string.start_screen),
            isSingleSelect = true,
            list = list,
            onChecked = { index, _ ->
                val i = if (ScreenUtils.isTablet) index + 1 else index
                setMainCheckBox(Const.START_SCEEN, i)
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
        list.add(CheckItem(getString(R.string.doctrine_creator), SettingsToiler.CLEAR_DOCTRINE))
        list.add(CheckItem(getString(R.string.articles), SettingsToiler.CLEAR_ARTICLES))
        list.add(CheckItem(getString(R.string.markers), SettingsToiler.CLEAR_MARKERS))

        adapter.addItem(
            SettingsItem.CheckListButton(
                title = getString(R.string.free_storage),
                list = list,
                buttonLabel = getString(R.string.delete),
                onClick = {
                    setStatus(true)
                    toiler.startClear(it)
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

        adapter.addItem(SettingsItem.Notification(
            title = getString(R.string.notif_new),
            offLabel = getString(R.string.less),
            onLabel = getString(R.string.often),
            valueSeek = v,
            maxSeek = CHECK_MAX,
            changeValue = this::setCheckTime,
            stopTracking = this::saveCheck,
            onClick = {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    dialog = SetNotifDialog(requireActivity(), SummaryHelper.TAG)
                    dialog?.show()
                } else {
                    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, act!!.packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, NotificationUtils.CHANNEL_SUMMARY)
                    startActivity(intent)
                }
            }
        ))
    }

    private fun initPromSection() {
        var v = prefProm.getInt(Const.TIME, Const.TURN_OFF)
        if (v == Const.TURN_OFF)
            v = PROM_MAX

        adapter.addItem(SettingsItem.Notification(
            title = getString(R.string.notif_prom),
            offLabel = getString(R.string.advance),
            onLabel = getString(R.string.prom),
            valueSeek = v,
            maxSeek = PROM_MAX,
            changeValue = this::setPromTime,
            stopTracking = this::saveProm,
            onClick = {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    dialog = SetNotifDialog(requireActivity(), PromUtils.TAG)
                    dialog?.show()
                } else {
                    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, act!!.packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, NotificationUtils.CHANNEL_PROM)
                    startActivity(intent)
                }
            }
        ))
    }

    private fun initMessageSection() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            adapter.addItem(SettingsItem.Message(
                title = getString(R.string.about_manager),
                text = getString(R.string.info_manager),
                buttonLabel = "",
                onClick = {}
            ))
            return
        }

        adapter.addItem(SettingsItem.Message(
            title = getString(R.string.about_manager),
            text = getString(R.string.info_manager) + Const.NN + getString(R.string.info_battery),
            buttonLabel = getString(R.string.set_battery),
            onClick = {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            }
        ))
    }

    private fun setMainCheckBox(name: String, value: Int) {
        val editor = prefMain.edit()
        editor.putInt(name, value)
        editor.apply()
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
            if (v == 15) v = 20
        } else v = Const.TURN_OFF
        editor.putInt(Const.TIME, v)
        editor.apply()
        CheckStarter.set(v)
    }

    private fun saveProm(value: Int) {
        val editor = prefProm.edit()
        val v = if (value == PROM_MAX)
            Const.TURN_OFF else value
        editor.putInt(Const.TIME, v)
        editor.apply()
        val prom = PromUtils(null)
        prom.initNotif(v)
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
        data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.let { uri ->
            val ringTone = RingtoneManager.getRingtone(act, uri)
            dialog?.putRingtone(ringTone.getTitle(act), uri.toString())
        }
    }
}