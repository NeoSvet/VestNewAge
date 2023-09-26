package ru.neosvet.vestnewage.view.fragment

import android.animation.Animator
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.CalendarItem
import ru.neosvet.vestnewage.databinding.CalendarFragmentBinding
import ru.neosvet.vestnewage.helper.DateHelper
import ru.neosvet.vestnewage.service.LoaderService
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.BrowserActivity
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.dialog.DownloadDialog
import ru.neosvet.vestnewage.view.list.CalendarAdapter
import ru.neosvet.vestnewage.view.list.TabAdapter
import ru.neosvet.vestnewage.viewmodel.CalendarToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.CalendarState
import ru.neosvet.vestnewage.viewmodel.state.NeoState

class CalendarFragment : NeoFragment() {
    private val toiler: CalendarToiler
        get() = neotoiler as CalendarToiler
    private var binding: CalendarFragmentBinding? = null
    private val adapter = CalendarAdapter(this::onClick)
    override val title: String
        get() = getString(R.string.calendar)
    private var openedReader = false
    private var shownDwnDialog = false
    private lateinit var yearAdapter: TabAdapter
    private lateinit var monthAdapter: TabAdapter

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
        initDatePicker()
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
                shownDwnDialog = shownDwnDialog
            )
        )
        super.onSaveInstanceState(outState)
    }

    private fun initDatePicker() = binding?.run {
        monthAdapter = TabAdapter(null, null, !ScreenUtils.isLand) {
            toiler.openList(month = it)
        }
        rvMonth.adapter = monthAdapter
        yearAdapter = TabAdapter(btnPrevYear, btnNextYear, !ScreenUtils.isLand) {
            toiler.openList(year = it)
        }
        rvYear.adapter = yearAdapter
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initCalendar() = binding?.run {
        val layoutManager = GridLayoutManager(requireContext(), 7)
        rvCalendar.layoutManager = layoutManager
        rvCalendar.adapter = adapter
        btnPrevMonth.setOnClickListener { openMonth(false) }
        btnNextMonth.setOnClickListener { openMonth(true) }
    }

    private fun openLink(link: String) {
        openedReader = true
        BrowserActivity.openReader(link, null)
    }

    private fun openMonth(plus: Boolean) {
        if (isBlocked) return
        binding?.run {
            val month = rvMonth.adapter as TabAdapter
            val year = rvYear.adapter as TabAdapter
            if (plus) {
                val maxMonth = month.itemCount - 1
                val maxYear = year.itemCount - 1
                if (month.selected == maxMonth && year.selected == maxYear) {
                    toiler.openList(month = 0)
                    return
                }
            } else if (month.selected == 0 && year.selected == 0) {
                if (!DateHelper.isLoadedOtkr() && !LoaderService.isRun)
                    showDownloadDialog()
                else toiler.openList(month = month.itemCount - 1)
                return
            }
        }
        if (plus) toiler.openList(month = -2)
        else toiler.openList(month = -3)
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
        yearAdapter.isBlocked = isBlocked
        monthAdapter.isBlocked = isBlocked
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

    @SuppressLint("SetTextI18n")
    override fun onChangedOtherState(state: NeoState) {
        when (state) {
            is BasicState.NotLoaded ->
                binding?.tvUpdate?.text = getString(R.string.list_no_loaded)

            is CalendarState.Status ->
                restoreStatus(state)

            BasicState.Success ->
                setStatus(false)

            is CalendarState.Primary -> binding?.run {
                setUpdateTime(state.time, tvUpdate)
                tvUpdate.text = state.label + ". " + tvUpdate.text
                adapter.setItems(state.list)
                if (rvCalendar.isVisible.not())
                    showView(rvCalendar)
                monthAdapter.setItems(state.months)
                monthAdapter.select(state.selected.y)
                yearAdapter.setItems(state.years)
                yearAdapter.select(state.selected.x)
                if (state.isUpdateUnread)
                    act?.updateNew()
            }
        }
    }

    private fun restoreStatus(state: CalendarState.Status) {
        if (state.shownDwnDialog)
            showDownloadDialog()
    }

    private fun showView(view: View) {
        view.isVisible = true
        view.alpha = 0f
        view.animate()
            .setListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationEnd(animation: Animator) {
                    binding?.run {
                        rvYear.smoothScrollToPosition(yearAdapter.selected)
                        rvMonth.smoothScrollToPosition(monthAdapter.selected)
                    }
                }

                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            })
            .alpha(1f)
            .setDuration(225)
            .start()
    }

    override fun onAction(title: String) {
        startLoad()
    }
}