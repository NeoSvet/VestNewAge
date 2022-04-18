package ru.neosvet.vestnewage.fragment

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.work.Data
import com.google.android.material.tabs.TabLayout
import ru.neosvet.ui.NeoFragment
import ru.neosvet.ui.Tip
import ru.neosvet.ui.dialogs.CustomDialog
import ru.neosvet.ui.dialogs.DateDialog
import ru.neosvet.ui.select
import ru.neosvet.utils.Const
import ru.neosvet.utils.DataBase
import ru.neosvet.utils.Lib
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.activity.MarkerActivity
import ru.neosvet.vestnewage.databinding.BookFragmentBinding
import ru.neosvet.vestnewage.helpers.BookHelper
import ru.neosvet.vestnewage.helpers.DateHelper
import ru.neosvet.vestnewage.helpers.LoaderHelper
import ru.neosvet.vestnewage.list.ListAdapter
import ru.neosvet.vestnewage.model.BookModel
import ru.neosvet.vestnewage.model.basic.*
import java.util.*
import kotlin.math.abs

class BookFragment : NeoFragment(), DateDialog.Result, Observer<NeoState> {
    companion object {
        fun newInstance(tab: Int, year: Int): BookFragment {
            val fragment = BookFragment()
            fragment.arguments = Bundle().apply {
                putInt(Const.TAB, tab)
                putInt(Const.YEAR, year)
            }
            return fragment
        }

        private const val DIALOG_DATE = "date"
    }

    private lateinit var anMin: Animation
    private lateinit var anMax: Animation
    private lateinit var adBook: ListAdapter
    private var dateDialog: DateDialog? = null
    private var alertRnd: CustomDialog? = null
    private var x = 0
    private var y = 0
    private lateinit var menuRnd: Tip
    private var binding: BookFragmentBinding? = null
    private val model: BookModel by lazy {
        ViewModelProvider(this).get(BookModel::class.java)
    }
    private var dialog: String? = ""
    private var notClick = false
    private val helper: BookHelper
        get() = model.helper!!
    val hTimer = Handler {
        binding?.tvDate?.setBackgroundResource(R.drawable.card_bg)
        false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = BookFragmentBinding.inflate(inflater, container, false).also {
        binding = it
    }.root

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (model.helper == null) {
            model.init(requireContext())
            arguments?.let {
                model.selectedTab = it.getInt(Const.TAB)
                val year = it.getInt(Const.YEAR)
                if (year > 0) {
                    val d = DateHelper.initToday()
                    d.year = year
                    dialog = DIALOG_DATE + d
                    showDatePicker(d)
                }
            }
        }
        setViews()
        initTabs()
        model.state.observe(act, this)
        restoreState(savedInstanceState)
        if (model.isRun)
            setStatus(true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        dialog?.let {
            outState.putString(Const.DIALOG, it)
            if (it.contains(DIALOG_DATE)) dateDialog!!.dismiss()
            else if (it.length > 1) alertRnd!!.dismiss()
        }
        super.onSaveInstanceState(outState)
    }

    private fun restoreState(state: Bundle?) {
        if (state != null && model.isRun.not()) {
            state.getString(Const.DIALOG)?.let {
                dialog = it
                if (it.contains(DIALOG_DATE)) {
                    if (it != DIALOG_DATE) {
                        val d = DateHelper.parse(it.substring(DIALOG_DATE.length))
                        showDatePicker(d)
                    } else
                        showDatePicker(model.date)
                } else if (it.length > 1) {
                    val m = it.split(Const.AND).toTypedArray()
                    showRndAlert(m[0], m[1], m[2], m[3], m[4].toInt())
                }
            }
        }
        binding?.tablayout?.select(model.selectedTab)
    }

    private fun initTabs() = binding?.run {
        tablayout.addTab(tablayout.newTab().setText(R.string.katreny))
        tablayout.addTab(tablayout.newTab().setText(R.string.poslaniya))
        if (model.isKatrenTab)
            act.title = getString(R.string.katreny)
        else
            tablayout.select(model.selectedTab)

        tablayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab) {
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabSelected(tab: TabLayout.Tab) {
                model.cancel()
                act.title = tab.text
                model.selectedTab = tab.position
                model.openList(true)
            }
        })
    }

    override fun onChanged(state: NeoState) {
        when (state) {
            is ProgressState ->
                act.status.setProgress(state.percent)
            NeoState.Loading ->
                setStatus(true)
            is SuccessBook ->
                setBook(state)
            is MessageState ->
                Lib.showToast(state.message)
            is SuccessRnd -> with(state) {
                dialog = title + Const.AND + link + Const.AND + msg +
                        Const.AND + place + Const.AND + par
                showRndAlert(title, link, msg, place, par)
            }
            is NeoState.Error -> {
                setStatus(false)
                act.status.setError(state.throwable.localizedMessage)
            }
        }
    }

    private fun setBook(state: SuccessBook) = binding?.run {
        setStatus(false)
        act.updateNew()
        tvDate.text = state.date
        ivPrev.isEnabled = state.prev
        ivNext.isEnabled = state.next
        adBook.setItems(state.list)
        lvBook.smoothScrollToPosition(0)
    }

    override fun onChanged(data: Data) {
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setViews() = binding?.run {
        ivPrev.isEnabled = false
        ivNext.isEnabled = false
        menuRnd = Tip(requireContext(), pRnd)
        bRndStih.setOnClickListener {
            menuRnd.hide()
            model.getRnd(BookModel.RndType.STIH)
        }
        bRndPos.setOnClickListener {
            menuRnd.hide()
            model.getRnd(BookModel.RndType.POS)
        }
        bRndKat.setOnClickListener {
            menuRnd.hide()
            model.getRnd(BookModel.RndType.KAT)
        }
        anMin = AnimationUtils.loadAnimation(act, R.anim.minimize)
        anMin.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                fabRefresh.isVisible = false
                fabRndMenu.isVisible = false
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
        anMax = AnimationUtils.loadAnimation(act, R.anim.maximize)

        fabRefresh.setOnClickListener { startLoad() }
        adBook = ListAdapter(requireContext())
        lvBook.adapter = adBook
        lvBook.onItemClickListener =
            OnItemClickListener { adapterView: AdapterView<*>?, view: View?, pos: Int, l: Long ->
                if (notClick) return@OnItemClickListener
                if (act.checkBusy()) return@OnItemClickListener
                openReader(adBook.getItem(pos).link, null)
            }
        lvBook.setOnTouchListener { _, event: MotionEvent ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!act.status.startMin()) {
                        fabRefresh.startAnimation(anMin)
                        fabRndMenu.startAnimation(anMin)
                        menuRnd.hide()
                    }
                    x = event.getX(0).toInt()
                    y = event.getY(0).toInt()
                }
                MotionEvent.ACTION_UP -> {
                    val x2 = event.getX(0).toInt()
                    val r = Math.abs(x - x2)
                    notClick = false
                    if (r > (30 * resources.displayMetrics.density).toInt() &&
                        r > abs(y - event.getY(0).toInt())
                    ) {
                        if (x > x2) { // next
                            if (ivNext.isEnabled) openMonth(true)
                            notClick = true
                        } else if (x < x2) { // prev
                            if (ivPrev.isEnabled) openMonth(false)
                            notClick = true
                        }
                    }
                    if (!act.status.startMax()) {
                        fabRefresh.isVisible = true
                        fabRefresh.startAnimation(anMax)
                        fabRndMenu.isVisible = true
                        fabRndMenu.startAnimation(anMax)
                    }
                }
                MotionEvent.ACTION_CANCEL -> if (!act.status.startMax()) {
                    fabRefresh.isVisible = true
                    fabRefresh.startAnimation(anMax)
                    fabRndMenu.isVisible = true
                    fabRndMenu.startAnimation(anMax)
                }
            }
            false
        }
        ivPrev.setOnClickListener { openMonth(false) }
        ivNext.setOnClickListener { openMonth(true) }
        act.status.setClick { onStatusClick(false) }
        tvDate.setOnClickListener {
            if (act.checkBusy()) return@setOnClickListener
            dialog = DIALOG_DATE
            showDatePicker(model.date)
        }
        fabRndMenu.setOnClickListener {
            if (menuRnd.isShow) menuRnd.hide()
            else menuRnd.show()
        }
    }

    override fun onStatusClick(reset: Boolean) {
        model.cancel()
        binding?.run {
            fabRefresh.isVisible = true
            fabRndMenu.isVisible = true
        }
        model.openList(false)
        setStatus(false)
        if (reset) {
            act.status.setError(null)
            return
        }
        if (!act.status.onClick() && act.status.isTime) startLoad()
    }

    private fun openMonth(plus: Boolean) {
        if (act.checkBusy()) return
        val d = model.date
        if (!plus && model.isKatrenTab.not()) {
            if (d.month == 1 && d.year == 2016 && helper.isLoadedOtkr().not()) {
                showAlertDownloadOtkr()
                return
            }
        }
        if (plus) d.changeMonth(1)
        else d.changeMonth(-1)
        blinkDate()
        model.openList(true)
    }

    private fun blinkDate() {
        binding?.tvDate?.setBackgroundResource(R.drawable.selected)
        Timer().schedule(object : TimerTask() {
            override fun run() {
                hTimer.sendEmptyMessage(1)
            }
        }, 300)
    }

    private fun showAlertDownloadOtkr() {
        val builder = AlertDialog.Builder(act, R.style.NeoDialog)
        builder.setMessage(getString(R.string.alert_download_otkr))
        builder.setNegativeButton(
            getString(R.string.no)
        ) { dialog: DialogInterface, _ -> dialog.dismiss() }
        builder.setPositiveButton(
            getString(R.string.yes)
        ) { _, _ ->
            binding?.ivPrev?.isEnabled = false
            LoaderHelper.postCommand(LoaderHelper.DOWNLOAD_OTKR, "")
        }
        builder.create().show()
    }

    override fun startLoad() {
        if (model.isRun) return
        setStatus(true)
        model.load()
    }

    override fun setStatus(load: Boolean) {
        binding?.run {
            val tabHost = tablayout.getChildAt(0) as ViewGroup
            if (load) {
                tabHost.getChildAt(0).isEnabled = false
                tabHost.getChildAt(1).isEnabled = false
                tvDate.isEnabled = false
                ivPrev.isEnabled = false
                ivNext.isEnabled = false
                fabRefresh.isVisible = false
                fabRndMenu.isVisible = false
                act.status.setLoad(true)
            } else {
                tabHost.getChildAt(0).isEnabled = true
                tabHost.getChildAt(1).isEnabled = true
                tvDate.isEnabled = true
                fabRefresh.isVisible = true
                fabRndMenu.isVisible = true
                if (act.status.isVisible)
                    act.status.setLoad(false)
            }
        }
    }

    private fun showDatePicker(d: DateHelper) {
        dateDialog = DateDialog(act, d).apply {
            setResult(this@BookFragment)
            if (model.isKatrenTab) {
                setMinMonth(2) //feb
            } else { //poslyania
                if (helper.isLoadedOtkr()) {
                    setMinMonth(8) //aug
                    setMinYear(2004)
                }
                setMaxMonth(9) //sep
                setMaxYear(2016)
            }
        }
        dateDialog?.show()
    }

    override fun putDate(date: DateHelper?) {
        dialog = ""
        if (date == null) //cancel
            return
        model.date = date
        model.openList(true)
    }

    private fun showRndAlert(title: String, link: String, msg: String, place: String, par: Int) {
        alertRnd = CustomDialog(act).apply {
            setTitle(title)
            setMessage(msg)
            setLeftButton(getString(R.string.in_markers)) {
                val marker = Intent(requireContext(), MarkerActivity::class.java)
                marker.putExtra(Const.LINK, link)
                marker.putExtra(DataBase.PARAGRAPH, par)
                startActivity(marker)
                alertRnd?.dismiss()
            }
            setRightButton(getString(R.string.open)) {
                openReader(link, place)
                alertRnd?.dismiss()
            }
        }
        alertRnd?.show { dialog = "" }
    }
}