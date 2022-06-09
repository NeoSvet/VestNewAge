package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.CalendarItem
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.databinding.CalendarFragmentBinding
import ru.neosvet.vestnewage.helper.BookHelper
import ru.neosvet.vestnewage.service.LoaderService
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.view.activity.BrowserActivity
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.dialog.DateDialog
import ru.neosvet.vestnewage.view.list.CalendarAdapter
import ru.neosvet.vestnewage.view.list.CalendarAdapter.Clicker
import ru.neosvet.vestnewage.viewmodel.CalendarToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.basic.Ready
import ru.neosvet.vestnewage.viewmodel.basic.SuccessCalendar

class CalendarFragment : NeoFragment(), DateDialog.Result, Clicker {
    private val toiler: CalendarToiler
        get() = neotoiler as CalendarToiler
    private var binding: CalendarFragmentBinding? = null
    private val adCalendar: CalendarAdapter = CalendarAdapter(this)
    private var dateDialog: DateDialog? = null
    private var dialog = false
    override val title: String
        get() = getString(R.string.calendar)
    private var openedReader = false
    private var isBlocked = false

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
        outState.putBoolean(Const.DIALOG, dialog)
        if (dialog) dateDialog?.dismiss()
        super.onSaveInstanceState(outState)
    }

    private fun restoreState(state: Bundle?) {
        toiler.openCalendar(0)
        state?.let {
            dialog = it.getBoolean(Const.DIALOG)
            if (dialog) showDatePicker()
        }
        if (state == null && toiler.isNeedReload()) {
            startLoad()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initCalendar() = binding?.run {
        val layoutManager = GridLayoutManager(requireContext(), 7)
        rvCalendar.layoutManager = layoutManager
        rvCalendar.adapter = adCalendar
        svCalendar.setOnScrollChangeListener { _, _, _, _, _ ->
            if (toiler.isRun) return@setOnScrollChangeListener
            if (svCalendar.canScrollVertically(50)) {
                if (isBlocked) {
                    act?.unblocked()
                    isBlocked = false
                }
            } else if (isBlocked.not()) {
                act?.blocked()
                isBlocked = true
            }
        }
        bPrev.setOnClickListener { openMonth(-1) }
        bNext.setOnClickListener { openMonth(1) }
        tvDate.setOnClickListener { showDatePicker() }
    }

    private fun openLink(link: String) {
        openedReader = true
        BrowserActivity.openReader(link, null)
    }

    private fun openMonth(offset: Int) {
        binding?.tvDate?.let {
            it.setBackgroundResource(R.drawable.selected)
            lifecycleScope.launch {
                delay(300)
                it.post { it.setBackgroundResource(R.drawable.card_bg) }
            }
        }
        toiler.openCalendar(offset)
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

    private fun showDatePicker() {
        dialog = true
        dateDialog = DateDialog(act, toiler.date).apply {
            val book = BookHelper()
            if (book.isLoadedOtkr()) {
                setMinMonth(8)
                setMinYear(2004)
            }
            setResult(this@CalendarFragment)
        }
        dateDialog?.show()
    }

    override fun putDate(date: DateUnit?) {
        dialog = false
        if (date == null) // cancel
            return
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

    override fun onChangedState(state: NeoState) {
        if (toiler.isRun.not())
            setStatus(false)
        if (state is SuccessCalendar) binding?.run {
            act?.updateNew()
            tvDate.text = state.date
            bPrev.isEnabled = state.prev
            bNext.isEnabled = state.next
            adCalendar.setItems(state.list)
        } else if (state == Ready)
            Lib.showToast(getString(R.string.load_unavailable))
    }

    override fun onAction(title: String) {
        when (title) {
            getString(R.string.download_calendar) ->
                LoaderService.postCommand(
                    LoaderService.DOWNLOAD_YEAR, toiler.date.year.toString()
                )
            getString(R.string.refresh) ->
                startLoad()
        }
    }
}