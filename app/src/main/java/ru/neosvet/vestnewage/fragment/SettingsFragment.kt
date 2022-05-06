package ru.neosvet.vestnewage.fragment

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
import android.widget.CompoundButton
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import ru.neosvet.ui.NeoFragment
import ru.neosvet.ui.dialogs.SetNotifDialog
import ru.neosvet.utils.Const
import ru.neosvet.utils.DataBase
import ru.neosvet.utils.Lib
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.activity.MainActivity
import ru.neosvet.vestnewage.databinding.SettingsFragmentBinding
import ru.neosvet.vestnewage.helpers.NotificationHelper
import ru.neosvet.vestnewage.helpers.PromHelper
import ru.neosvet.vestnewage.model.SettingsModel
import ru.neosvet.vestnewage.model.basic.CheckTime
import ru.neosvet.vestnewage.model.basic.NeoState
import ru.neosvet.vestnewage.model.basic.NeoViewModel
import ru.neosvet.vestnewage.service.CheckStarter
import java.util.concurrent.TimeUnit

class SettingsFragment : NeoFragment() {
    private var binding: SettingsFragmentBinding? = null
    private var dialog: SetNotifDialog? = null
    private val prefMain: SharedPreferences by lazy {
        requireContext().getSharedPreferences(
            MainActivity::class.java.simpleName,
            Context.MODE_PRIVATE
        )
    }
    private val prefSummary: SharedPreferences by lazy {
        requireContext().getSharedPreferences(
            Const.SUMMARY, Context.MODE_PRIVATE
        )
    }
    private val prefProm: SharedPreferences by lazy {
        requireContext().getSharedPreferences(
            Const.PROM, Context.MODE_PRIVATE
        )
    }
    private val bPanels: BooleanArray
        get() = model.panels

    private val model: SettingsModel
        get() = neomodel as SettingsModel
    private val anRotate: Animation by lazy {
        initAnimation()
    }

    private var stopRotate = false

    override fun initViewModel(): NeoViewModel {
        return ViewModelProvider(this).get(SettingsModel::class.java)
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
        initMain()
        initBaseSection()
        initScreenSection()
        initClearSection()
        initCheckSection()
        initPromSection()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            initButtonsSet()
        else
            initButtonsSetNew()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onChangedState(state: NeoState) {
        when (state) {
            is CheckTime -> {
                setStatus(false)
                val size = state.sec / 1048576f //to MegaByte
                Lib.showToast(String.format(getString(R.string.format_freed_size), size))
            }
        }
    }

    override fun setStatus(load: Boolean) {
        if (load) {
            binding?.clear?.run {
                bClearDo.isEnabled = false
                stopRotate = false
                ivClear.isVisible = true
                ivClear.startAnimation(anRotate)
            }
        } else stopRotate = true
    }

    private fun initAnimation(): Animation {
        val animation = AnimationUtils.loadAnimation(requireContext(), R.anim.rotate)
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                binding?.clear?.run {
                    if (stopRotate)
                        ivClear.isVisible = false
                    else
                        ivClear.startAnimation(anRotate)
                }
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
        return animation
    }

    private fun initMain() = binding?.run {
        val imgSections = arrayOf(
            base.img, screen.img, clear.img, check.img, prom.img
        )
        val pSections = arrayOf(
            base.pnl, screen.pnl, clear.pnl, check.pnl, prom.pnl
        )
        val btnSections = arrayOf(
            base.btn, screen.btn, clear.btn, check.btn, prom.btn
        )
        val listener = View.OnClickListener { view: View ->
            val i = btnSections.indexOf(view)
            if (bPanels[i]) { //if open
                pSections[i].isVisible = false
                imgSections[i].setImageResource(R.drawable.plus)
            } else { //if close
                pSections[i].isVisible = true
                imgSections[i].setImageResource(R.drawable.minus)
            }
            bPanels[i] = bPanels[i].not()
        }
        for (button in btnSections)
            button.setOnClickListener(listener)

        for (i in bPanels.indices) {
            if (bPanels[i]) {
                pSections[i].isVisible = true
                imgSections[i].setImageResource(R.drawable.minus)
            }
        }
    }

    private fun initBaseSection() = binding?.base?.run {
        cbCountFloat.isChecked = !MainActivity.isCountInMenu
        if (act?.isMenuMode == true)
            cbCountFloat.text = getString(R.string.count_everywhere)
        cbNew.isChecked = prefMain.getBoolean(Const.START_NEW, false)
        cbCountFloat.setOnCheckedChangeListener { _, check: Boolean ->
            setMainCheckBox(Const.COUNT_IN_MENU, !check, -1)
            MainActivity.isCountInMenu = !check
        }
        cbNew.setOnCheckedChangeListener { _, check: Boolean ->
            setMainCheckBox(
                Const.START_NEW, check, -1
            )
        }
    }

    private fun initScreenSection() = binding?.screen?.run {
        val rbsScreen = arrayOf(rbMenu, rbCalendar, rbSummary)
        if (resources.getInteger(R.integer.screen_mode) >= resources.getInteger(R.integer.screen_tablet_port))
            rbsScreen[0].isVisible = false
        val p = prefMain.getInt(Const.START_SCEEN, Const.SCREEN_CALENDAR)
        rbsScreen[p].isChecked = true

        val listener =
            CompoundButton.OnCheckedChangeListener { view: CompoundButton, checked: Boolean ->
                if (checked) {
                    val i = rbsScreen.indexOf(view)
                    setMainCheckBox(Const.START_SCEEN, false, i)
                }
            }
        for (radioButton in rbsScreen)
            radioButton.setOnCheckedChangeListener(listener)
    }

    private fun initClearSection() = binding?.clear?.run {
        val cbsClear = arrayOf(cbBookPrev, cbBookCur, cbMaterials, cbMarkers, cbCache)

        val listener = CompoundButton.OnCheckedChangeListener { _, _ ->
            var k = 0
            for (checkBox in cbsClear)
                if (checkBox.isChecked) k++
            bClearDo.isEnabled = k > 0
        }
        for (checkBox in cbsClear)
            checkBox.setOnCheckedChangeListener(listener)

        bClearDo.setOnClickListener {
            setStatus(true)
            val list = mutableListOf<String>()
            if (cbsClear[0].isChecked) //book prev years
                list.add(Const.START)
            if (cbsClear[1].isChecked) //book cur year
                list.add(Const.END)
            if (cbsClear[2].isChecked) //materials
                list.add(DataBase.ARTICLES)
            if (cbsClear[3].isChecked) //markers
                list.add(DataBase.MARKERS)
            if (cbsClear[4].isChecked) //cache
                list.add(Const.FILE)
            model.startClear(list)
            for (checkBox in cbsClear)
                checkBox.isChecked = false
        }
    }

    private fun initCheckSection() = binding?.check?.run {
        var p = prefSummary.getInt(Const.TIME, Const.TURN_OFF)
        if (p == Const.TURN_OFF)
            sbCheckTime.progress = sbCheckTime.max
        else {
            p /= 15
            if (p > 3) p = p / 4 + 2 else p--
            sbCheckTime.progress = p
        }
        setCheckTime()

        sbCheckTime.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                setCheckTime()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                saveCheck()
            }
        })
    }

    private fun initPromSection() = binding?.prom?.run {
        val p = prefProm.getInt(Const.TIME, Const.TURN_OFF)
        if (p == Const.TURN_OFF)
            sbPromTime.progress = sbPromTime.max
        else
            sbPromTime.progress = p
        setPromTime()

        sbPromTime.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                setPromTime()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                saveProm()
            }
        })
    }

    private fun initButtonsSet() = binding?.run {
        check.bCheckSet.setOnClickListener {
            dialog = SetNotifDialog(requireActivity(), Const.SUMMARY)
            dialog?.show()
        }
        prom.bPromSet.setOnClickListener {
            dialog = SetNotifDialog(requireActivity(), Const.PROM)
            dialog?.show()
        }
    }

    @RequiresApi(26)
    private fun initButtonsSetNew() = binding?.run {
        check.bCheckSet.setOnClickListener {
            val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, act!!.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, NotificationHelper.CHANNEL_SUMMARY)
            startActivity(intent)
        }
        prom.bPromSet.setOnClickListener {
            val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, act!!.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, NotificationHelper.CHANNEL_PROM)
            startActivity(intent)
        }
        pBattery.isVisible = true
        bSetBattery.setOnClickListener {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        }
    }

    private fun setMainCheckBox(name: String, check: Boolean, value: Int) {
        val editor = prefMain.edit()
        if (value == -1)
            editor.putBoolean(name, check)
        else
            editor.putInt(name, value)
        editor.apply()
        if (name == Const.START_NEW) return
        val main = Intent(act, MainActivity::class.java)
        if (value == -1) main.putExtra(Const.CUR_ID, R.id.nav_settings)
        startActivity(main)
        act?.finish()
    }

    private fun saveCheck() = binding?.check?.run {
        val editor = prefSummary.edit()
        var p = sbCheckTime.progress
        if (p < sbCheckTime.max) {
            if (p > 2) p = (p - 2) * 4 else p++
            p *= 15
        } else p = Const.TURN_OFF
        editor.putInt(Const.TIME, p)
        editor.apply()
        val work = WorkManager.getInstance(App.context)
        work.cancelAllWorkByTag(CheckStarter.TAG_PERIODIC)
        if (p == Const.TURN_OFF) return@run
        if (p == 15) p = 20
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val task = PeriodicWorkRequest.Builder(
            CheckStarter::class.java,
            p.toLong(),
            TimeUnit.MINUTES,
            (p - 5).toLong(),
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag(CheckStarter.TAG_PERIODIC)
            .build()
        work.enqueue(task)
    }

    private fun saveProm() = binding?.prom?.run {
        val editor = prefProm.edit()
        var p = sbPromTime.progress
        if (p == sbPromTime.max) p = Const.TURN_OFF
        editor.putInt(Const.TIME, p)
        editor.apply()
        val prom = PromHelper(null)
        prom.initNotif(p)
    }

    private fun setCheckTime() = binding?.check?.run {
        val t = StringBuilder(getString(R.string.check_summary))
        t.append(" ")
        if (sbCheckTime.progress == sbCheckTime.max) {
            tvCheckOn.isVisible = true
            tvCheckOff.isVisible = false
            t.append(getString(R.string.turn_off))
            tvCheck.text = t
            bCheckSet.isEnabled = false
            return@run
        }
        bCheckSet.isEnabled = true
        tvCheckOn.isVisible = false
        tvCheckOff.isVisible = true
        var p = sbCheckTime.progress + 1
        var bH = false
        if (p > 3) {
            bH = true
            p -= 3
        } else p *= 15
        if (p == 1)
            t.append(getString(R.string.each_one))
        else {
            t.append(getString(R.string.each_more))
            t.append(" ")
            t.append(p)
            t.append(" ")
            if (bH) t.append(getString(R.string.hours))
            else t.append(getString(R.string.minutes))
        }
        tvCheck.text = t
    }

    private fun setPromTime() = binding?.prom?.run {
        var p = sbPromTime.progress
        if (p == sbPromTime.max) {
            tvPromOn.isVisible = true
            tvPromOff.isVisible = false
            tvPromNotif.text = getString(R.string.prom_notif_off)
            bPromSet.isEnabled = false
            return@run
        }
        bPromSet.isEnabled = true
        tvPromOn.isVisible = false
        tvPromOff.isVisible = true
        val t = StringBuilder(getString(R.string.prom_notif))
        t.append(" ")
        t.append(getString(R.string.`in`))
        t.append(" ")
        p++
        when (p) {
            1 ->
                t.append(resources.getStringArray(R.array.time)[3])
            in 5..20 -> {
                t.append(p)
                t.append(" ")
                t.append(resources.getStringArray(R.array.time)[4])
            }
            else -> {
                t.append(p)
                t.append(" ")
                when (p % 10) {
                    1 -> t.append(resources.getStringArray(R.array.time)[3])
                    in 2..4 -> t.append(resources.getStringArray(R.array.time)[5])
                    else -> t.append(resources.getStringArray(R.array.time)[4])
                }
            }
        }
        tvPromNotif.text = t
    }

    fun putRingtone(data: Intent?) {
        data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.let { uri ->
            val ringTone = RingtoneManager.getRingtone(act, uri)
            dialog?.putRingtone(ringTone.getTitle(act), uri.toString())
        }
    }
}