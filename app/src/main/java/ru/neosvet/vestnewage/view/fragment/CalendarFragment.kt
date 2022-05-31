package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.view.*
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.CalendarItem
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.databinding.CalendarFragmentBinding
import ru.neosvet.vestnewage.helper.BookHelper
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
import java.util.*

class CalendarFragment : NeoFragment(), DateDialog.Result, Clicker {
    private val toiler: CalendarToiler
        get() = neotoiler as CalendarToiler
    private var binding: CalendarFragmentBinding? = null
    private val adCalendar: CalendarAdapter = CalendarAdapter(this)
    private var dateDialog: DateDialog? = null
    private var dialog = false
    val currentYear: Int
        get() = toiler.date.year
    val hTimer = Handler { message: Message ->
        if (message.what == 0)
            binding?.tvDate?.setBackgroundResource(R.drawable.card_bg)
        else act?.startShowButtons()
        false
    }
    override val title: String
        get() = getString(R.string.calendar)
    private var openedReader = false

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
        setViews()
        initCalendar()
        restoreState(savedInstanceState)
        if (savedInstanceState == null && toiler.isNeedReload()) {
            startLoad()
        }
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
    }

    private fun setViews() = binding?.run {
        bProm.setOnClickListener { openLink("Posyl-na-Edinenie.html") }
        fabRefresh.setOnClickListener { startLoad() }
        act?.clearButtons()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initCalendar() = binding?.run {
        val layoutManager = GridLayoutManager(requireContext(), 7)
        rvCalendar.layoutManager = layoutManager
        rvCalendar.adapter = adCalendar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            rvCalendar.setOnTouchListener { _, event: MotionEvent ->
                if (act?.isInMultiWindowMode == false) return@setOnTouchListener false
                if (event.action == MotionEvent.ACTION_MOVE) {
                    if (animMinFinished) act?.startHideButtons()
                }
                if (event.action == MotionEvent.ACTION_UP) {
                    Timer().schedule(object : TimerTask() {
                        override fun run() {
                            hTimer.sendEmptyMessage(1)
                        }
                    }, 1000)
                }
                false
            }
        ivPrev.setOnClickListener { openMonth(-1) }
        ivNext.setOnClickListener { openMonth(1) }
        tvDate.setOnClickListener { showDatePicker() }
    }

    private fun openLink(link: String) {
        openedReader = true
        BrowserActivity.openReader(link, null)
    }

    private fun openMonth(offset: Int) {
        binding?.tvDate?.setBackgroundResource(R.drawable.selected)
        Timer().schedule(object : TimerTask() {
            override fun run() {
                hTimer.sendEmptyMessage(0)
            }
        }, 300)
        toiler.openCalendar(offset)
    }

    override fun setStatus(load: Boolean) {
        super.setStatus(load)
        binding?.run {
            if (load) {
                tvDate.isEnabled = false
                ivPrev.isEnabled = false
                ivNext.isEnabled = false
                fabRefresh.isVisible = false
            } else {
                tvDate.isEnabled = true
                fabRefresh.isVisible = true
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
            ivPrev.isEnabled = state.prev
            ivNext.isEnabled = state.next
            adCalendar.setItems(state.list)
        } else if (state == Ready)
            Lib.showToast(getString(R.string.load_unavailable))
    }
}