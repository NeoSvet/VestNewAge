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
import ru.neosvet.vestnewage.view.activity.BrowserActivity
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.dialog.DownloadDialog
import ru.neosvet.vestnewage.view.list.CalendarAdapter
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
                shownDwnDialog = shownDwnDialog
            )
        )
        super.onSaveInstanceState(outState)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initCalendar() = binding?.run {
        val layoutManager = GridLayoutManager(requireContext(), 7)
        rvCalendar.layoutManager = layoutManager
        rvCalendar.adapter = adapter
        pYear.setOnChangeListener {
            toiler.openList(year = it)
        }
        pMonth.setOnChangeListener {
            toiler.openList(month = it)
        }
        pMonth.btnPrev.setOnClickListener { openMonth(false) }
        pMonth.btnNext.setOnClickListener { openMonth(true) }
        pMonth.setDescription(getString(R.string.to_prev_month), getString(R.string.to_next_month))
        pYear.setDescription(getString(R.string.to_prev_year), getString(R.string.to_next_year))
    }

    private fun openLink(link: String) {
        openedReader = true
        BrowserActivity.openReader(link, null)
    }

    private fun openMonth(plus: Boolean) {
        if (isBlocked) return
        binding?.run {
            if (!plus && pMonth.selectedStart && pYear.selectedStart
                && !DateHelper.isLoadedOtkr() && !LoaderService.isRun
            ) {
                showDownloadDialog()
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
        binding?.run {
            pYear.isBlocked = isBlocked
            pMonth.isBlocked = isBlocked
        }
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
            is CalendarState.Status ->
                restoreStatus(state)

            BasicState.Success ->
                setStatus(false)

            BasicState.Ready ->
                act?.showToast(getString(R.string.finish_list))

            BasicState.Empty ->
                act?.showStaticToast(getString(R.string.empty_list))

            is CalendarState.Primary -> binding?.run {
                act?.hideToast()
                if (state.time == 0L) {
                    tvUpdate.text = state.label
                    act?.showStaticToast(getString(R.string.list_no_loaded))
                } else
                    setUpdateTime(state.time, tvUpdate, state.label + ". ")
                adapter.setItems(state.list)
                if (rvCalendar.isVisible.not())
                    showView(rvCalendar)
                pMonth.setItems(state.months, state.selected.y)
                pYear.setItems(state.years, state.selected.x)
                pMonth.fixWidth(1.3f)
                pYear.fixWidth(1f)
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
                        pYear.rvTab.smoothScrollToPosition(pYear.selectedIndex)
                        pMonth.rvTab.smoothScrollToPosition(pMonth.selectedIndex)
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