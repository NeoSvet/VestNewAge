package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.CalendarItem
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.databinding.CalendarFragmentBinding
import ru.neosvet.vestnewage.helper.DateHelper
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.view.activity.BrowserActivity
import ru.neosvet.vestnewage.view.activity.TipActivity
import ru.neosvet.vestnewage.view.activity.TipName
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.dialog.DateDialog
import ru.neosvet.vestnewage.view.dialog.DownloadDialog
import ru.neosvet.vestnewage.view.list.CalendarAdapter
import ru.neosvet.vestnewage.view.list.CalendarAdapter.Clicker
import ru.neosvet.vestnewage.viewmodel.CalendarToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler

class CalendarFragment : NeoFragment(), DateDialog.Result, Clicker {
    private val toiler: CalendarToiler
        get() = neotoiler as CalendarToiler
    private var binding: CalendarFragmentBinding? = null
    private val adCalendar: CalendarAdapter = CalendarAdapter(this)
    private var dateDialog: DateDialog? = null
    override val title: String
        get() = getString(R.string.calendar)
    private var openedReader = false
    private var shownDwnDialog = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = CalendarFragmentBinding.inflate(inflater, container, false).also {
        binding = it
    }.root

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this).get(CalendarToiler::class.java)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        disableUpdateRoot()
        initCalendar()
        restoreState(savedInstanceState)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        if (openedReader) {
            openedReader = false
            act?.updateNew()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (shownDwnDialog)
            outState.putInt(Const.DIALOG, 0)
        dateDialog?.let { d ->
            outState.putInt(Const.DIALOG, d.date.timeInDays)
            d.dismiss()
        }
        super.onSaveInstanceState(outState)
    }

    private fun restoreState(state: Bundle?) {
        if (state == null) {
            showTip()
            toiler.openCalendar(0)
            if (toiler.isNeedReload())
                startLoad()
        } else {
            val d = state.getInt(Const.DIALOG, -1)
            if (d > 0) {
                showDatePicker(DateUnit.putDays(d))
            } else if (d == 0)
                showDownloadDialog()
        }
    }

    private fun showTip() {
        TipActivity.showTipIfNeed(TipName.CALENDAR)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initCalendar() = binding?.run {
        val layoutManager = GridLayoutManager(requireContext(), 7)
        rvCalendar.layoutManager = layoutManager
        rvCalendar.adapter = adCalendar
        bPrev.setOnClickListener { openMonth(-1) }
        bNext.setOnClickListener { openMonth(1) }
        tvDate.setOnClickListener { showDatePicker(toiler.date) }
    }

    private fun openLink(link: String) {
        openedReader = true
        BrowserActivity.openReader(link, null)
    }

    private fun openMonth(offset: Int) {
        if (offset < 0 && toiler.date.timeInDays == DateHelper.MIN_DAYS_NEW_BOOK && !DateHelper.isLoadedOtkr()) {
            showDownloadDialog()
            return
        }
        toiler.cancel()
        binding?.tvDate?.let {
            it.setBackgroundResource(R.drawable.selected)
            lifecycleScope.launch {
                delay(300)
                it.post { it.setBackgroundResource(R.drawable.card_bg) }
            }
        }
        toiler.openCalendar(offset)
    }

    private fun showDownloadDialog() {
        shownDwnDialog = true
        val dialog = DownloadDialog(act!!, true).apply {
            setOnDismissListener { shownDwnDialog = false }
        }
        dialog.show()
    }

    override fun setStatus(load: Boolean) {
        super.setStatus(load)
        binding?.run {
            if (load) {
                tvDate.isEnabled = false
                bPrev.isEnabled = false
                bNext.isEnabled = false
            } else {
                tvDate.isEnabled = true
                bPrev.isEnabled = toiler.checkPrev()
                bNext.isEnabled = toiler.checkNext()
            }
        }
    }

    private fun showDatePicker(d: DateUnit) {
        dateDialog = DateDialog(act, d).apply {
            setResult(this@CalendarFragment)
            setOnDismissListener { dateDialog = null }
            show()
        }
    }

    override fun putDate(date: DateUnit?) {
        if (date != null)
            toiler.changeDate(date)
    }

    override fun onClick(view: View, item: CalendarItem) {
        if (toiler.isRun) return
        when (item.count) {
            1 -> {
                openLink(item.getLink(0))
                return
            }
            0 -> return
        }
        val pMenu = PopupMenu(requireContext(), view)
        for (i in 0 until item.count) {
            pMenu.menu.add(item.getTitle(i))
        }
        pMenu.setOnMenuItemClickListener { menuItem: MenuItem ->
            val title = menuItem.title.toString()
            for (i in 0 until item.count) {
                if (item.getTitle(i) == title) {
                    openLink(item.getLink(i))
                    break
                }
            }
            true
        }
        pMenu.show()
    }

    override fun onChangedOtherState(state: NeoState) {
        if (toiler.isRun.not())
            setStatus(false)
        when (state) {
            is NeoState.Calendar -> binding?.run {
                if(toiler.isUpdateUnread)
                    act?.updateNew()
                tvDate.text = state.date
                adCalendar.setItems(state.list)
                if (root.isVisible.not())
                    showView(root)
            }
            is NeoState.LongValue -> binding?.run {
                setUpdateTime(state.value, tvUpdate)
            }
            else -> {}
        }
    }

    private fun showView(view: View) {
        view.isVisible = true
        view.alpha = 0f
        view.animate()
            .alpha(1f)
            .setDuration(225)
            .start()
    }

    override fun onAction(title: String) {
        startLoad()
    }
}