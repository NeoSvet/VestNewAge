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
import ru.neosvet.vestnewage.view.activity.BrowserActivity
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.dialog.DateDialog
import ru.neosvet.vestnewage.view.dialog.DownloadDialog
import ru.neosvet.vestnewage.view.list.CalendarAdapter
import ru.neosvet.vestnewage.viewmodel.CalendarToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.CalendarState
import ru.neosvet.vestnewage.viewmodel.state.NeoState

class CalendarFragment : NeoFragment(), DateDialog.Result {
    private val toiler: CalendarToiler
        get() = neotoiler as CalendarToiler
    private var binding: CalendarFragmentBinding? = null
    private val adCalendar = CalendarAdapter(this::onClick)
    private var dateDialog: DateDialog? = null
    override val title: String
        get() = getString(R.string.calendar)
    private var openedReader = false
    private var shownDwnDialog = false
    private var date = DateUnit.initToday().apply { day = 1 }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = CalendarFragmentBinding.inflate(inflater, container, false).also {
        binding = it
    }.root

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this)[CalendarToiler::class.java]

    override fun onViewCreated(savedInstanceState: Bundle?) {
        disableUpdateRoot()
        initCalendar()
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
        toiler.setStatus(
            CalendarState.Status(
                dateDialog = dateDialog?.date,
                shownDwnDialog = shownDwnDialog
            )
        )
        super.onSaveInstanceState(outState)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initCalendar() = binding?.run {
        val layoutManager = GridLayoutManager(requireContext(), 7)
        rvCalendar.layoutManager = layoutManager
        rvCalendar.adapter = adCalendar
        bPrev.setOnClickListener { openMonth(-1) }
        bNext.setOnClickListener { openMonth(1) }
        tvDate.setOnClickListener { showDatePicker(date) }
    }

    private fun openLink(link: String) {
        openedReader = true
        BrowserActivity.openReader(link, null)
    }

    private fun openMonth(offset: Int) {
        if (offset < 0 && date.timeInDays == DateHelper.MIN_DAYS_NEW_BOOK && !DateHelper.isLoadedOtkr()) {
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
            }
        }
    }

    private fun showDatePicker(d: DateUnit) {
        dateDialog = DateDialog(requireActivity(), d, this).apply {
            setOnDismissListener { dateDialog = null }
            show()
        }
    }

    override fun putDate(date: DateUnit?) {
        if (date != null)
            toiler.changeDate(date)
    }

    private fun onClick(view: View, item: CalendarItem) {
        if (isBlocked) return
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
        when (state) {
            is BasicState.Success ->
                setStatus(false)

            is BasicState.NotLoaded ->
                binding?.tvUpdate?.text = getString(R.string.list_no_loaded)

            is CalendarState.Status ->
                restoreStatus(state)

            is CalendarState.Primary -> binding?.run {
                setStatus(false)
                date = state.date
                tvDate.text = date.calendarString
                bPrev.isEnabled = state.prev
                bNext.isEnabled = state.next
                setUpdateTime(state.time, tvUpdate)
                if (state.isUpdateUnread)
                    act?.updateNew()
                adCalendar.setItems(state.list)
                if (root.isVisible.not())
                    showView(root)
            }
        }
    }

    private fun restoreStatus(state: CalendarState.Status) {
        if (state.shownDwnDialog)
            showDownloadDialog()
        state.dateDialog?.let {
            showDatePicker(it)
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