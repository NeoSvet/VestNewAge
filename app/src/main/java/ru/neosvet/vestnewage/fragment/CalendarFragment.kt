package ru.neosvet.vestnewage.fragment

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.view.*
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.work.Data
import ru.neosvet.ui.NeoFragment
import ru.neosvet.ui.dialogs.DateDialog
import ru.neosvet.utils.Const
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.activity.BrowserActivity
import ru.neosvet.vestnewage.databinding.CalendarFragmentBinding
import ru.neosvet.vestnewage.helpers.BookHelper
import ru.neosvet.vestnewage.helpers.DateHelper
import ru.neosvet.vestnewage.helpers.ProgressHelper
import ru.neosvet.vestnewage.list.CalendarAdapter
import ru.neosvet.vestnewage.list.CalendarAdapter.Clicker
import ru.neosvet.vestnewage.list.CalendarItem
import ru.neosvet.vestnewage.model.CalendarModel
import ru.neosvet.vestnewage.model.basic.NeoState
import ru.neosvet.vestnewage.model.basic.ProgressState
import ru.neosvet.vestnewage.model.basic.SuccessCalendar
import java.util.*

class CalendarFragment : NeoFragment(), DateDialog.Result, Clicker,
    Observer<NeoState> {
    private val model: CalendarModel by lazy {
        ViewModelProvider(this).get(CalendarModel::class.java)
    }
    private var binding: CalendarFragmentBinding? = null
    private val adCalendar: CalendarAdapter = CalendarAdapter(this)
    private var dateDialog: DateDialog? = null
    private var dialog = false
    val currentYear: Int
        get() = model.date.year
    val hTimer = Handler { message: Message ->
        if (message.what == 0)
            binding?.tvDate?.setBackgroundResource(R.drawable.card_bg)
        else act.startAnimMax()
        false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = CalendarFragmentBinding.inflate(inflater, container, false).also {
        binding = it
    }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        act.title = getString(R.string.calendar)
        setViews()
        initCalendar()
        model.state.observe(act, this)
        restoreState(savedInstanceState)

        if (savedInstanceState == null && model.isNeedReload()) {
            startLoad()
        }
//        else if (ProgressHelper.isBusy())
//            setStatus(true)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onChanged(data: Data) {

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
        act.status.setClick { onStatusClick(false) }
        act.fab = fabRefresh
    }

    override fun onStatusClick(reset: Boolean) {
        if (model.isRun) {
            model.cancel()
            return
        }
        if (reset) {
            act.status.setError(null)
            return
        }
        if (!act.status.onClick() && act.status.isTime)
            startLoad()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initCalendar() = binding?.run {
        val layoutManager = GridLayoutManager(requireContext(), 7)
        rvCalendar.layoutManager = layoutManager
        rvCalendar.adapter = adCalendar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            rvCalendar.setOnTouchListener { _, event: MotionEvent ->
                if (!act.isInMultiWindowMode) return@setOnTouchListener false
                if (event.action == MotionEvent.ACTION_MOVE) {
                    act.startAnimMin()
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
        tvDate.setOnClickListener {
            if (act.checkBusy()) return@setOnClickListener
            showDatePicker()
        }
    }

    private fun openLink(link: String) {
        BrowserActivity.openReader(link, null)
    }

    private fun openMonth(offset: Int) {
        if (act.checkBusy()) return
        binding?.tvDate?.setBackgroundResource(R.drawable.selected)
        Timer().schedule(object : TimerTask() {
            override fun run() {
                hTimer.sendEmptyMessage(0)
            }
        }, 300)
        model.openCalendar(offset)
    }

    override fun startLoad() {
        model.load()
    }

    override fun setStatus(load: Boolean) {
        binding?.run {
            if (load) {
                ProgressHelper.setBusy(true)
                fabRefresh.isVisible = false
                act.status.setLoad(true)
                act.status.loadText()
            } else if (fabRefresh.isVisible.not()) {
                ProgressHelper.setBusy(false)
                fabRefresh.isVisible = true
                act.status.setLoad(false)
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
        if (act.checkBusy()) return
        when (item.count) {
            1 -> {
                openLink(item.getLink(0))
                return
            }
            0 -> return
        }
        val pMenu = PopupMenu(act, view)
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

    override fun onChanged(state: NeoState) {
        when (state) {
            is ProgressState ->
                act.status.setProgress(state.percent)
            NeoState.Loading ->
                setStatus(true)
            is SuccessCalendar<*> -> binding?.run {
                setStatus(false)
                act.updateNew()
                tvDate.text = state.date
                ivPrev.isEnabled = state.prev
                ivNext.isEnabled = state.next
                adCalendar.setItems(state.list as List<CalendarItem>)
            }
            is NeoState.Error -> {
                setStatus(false)
                act.status.setError(state.throwable.localizedMessage)
            }
        }
    }
}