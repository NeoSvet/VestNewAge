package ru.neosvet.vestnewage.fragment

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
import ru.neosvet.ui.NeoFragment
import ru.neosvet.ui.dialogs.DateDialog
import ru.neosvet.utils.Const
import ru.neosvet.utils.Lib
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.activity.BrowserActivity
import ru.neosvet.vestnewage.databinding.CalendarFragmentBinding
import ru.neosvet.vestnewage.helpers.BookHelper
import ru.neosvet.vestnewage.helpers.DateHelper
import ru.neosvet.vestnewage.list.CalendarAdapter
import ru.neosvet.vestnewage.list.CalendarAdapter.Clicker
import ru.neosvet.vestnewage.list.item.CalendarItem
import ru.neosvet.vestnewage.model.CalendarModel
import ru.neosvet.vestnewage.model.basic.NeoState
import ru.neosvet.vestnewage.model.basic.NeoViewModel
import ru.neosvet.vestnewage.model.basic.Ready
import ru.neosvet.vestnewage.model.basic.SuccessCalendar
import java.util.*

class CalendarFragment : NeoFragment(), DateDialog.Result, Clicker {
    private val model: CalendarModel
        get() = neomodel as CalendarModel
    private var binding: CalendarFragmentBinding? = null
    private val adCalendar: CalendarAdapter = CalendarAdapter(this)
    private var dateDialog: DateDialog? = null
    private var dialog = false
    val currentYear: Int
        get() = model.date.year
    val hTimer = Handler { message: Message ->
        if (message.what == 0)
            binding?.tvDate?.setBackgroundResource(R.drawable.card_bg)
        else act?.startAnimMax()
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

    override fun initViewModel(): NeoViewModel =
        ViewModelProvider(this).get(CalendarModel::class.java)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        setViews()
        initCalendar()
        restoreState(savedInstanceState)
        if (savedInstanceState == null && model.isNeedReload()) {
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
        model.openCalendar(0)
        state?.let {
            dialog = it.getBoolean(Const.DIALOG)
            if (dialog) showDatePicker()
        }
    }

    private fun setViews() = binding?.run {
        bProm.setOnClickListener { openLink("Posyl-na-Edinenie.html") }
        fabRefresh.setOnClickListener { startLoad() }
        act?.fab = fabRefresh
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
                    if (animMinFinished) act?.startAnimMin()
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
        model.openCalendar(offset)
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
        dateDialog = DateDialog(act, model.date).apply {
            val book = BookHelper()
            if (book.isLoadedOtkr()) {
                setMinMonth(8)
                setMinYear(2004)
            }
            setResult(this@CalendarFragment)
        }
        dateDialog?.show()
    }

    override fun putDate(date: DateHelper?) {
        dialog = false
        if (date == null) // cancel
            return
        model.changeDate(date)
    }

    override fun onClick(view: View, item: CalendarItem) {
        if (model.isRun) return
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