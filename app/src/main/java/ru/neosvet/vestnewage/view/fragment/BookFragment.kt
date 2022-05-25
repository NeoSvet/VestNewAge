package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.AdapterView.OnItemClickListener
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.tabs.TabLayout
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.databinding.BookFragmentBinding
import ru.neosvet.vestnewage.helper.BookHelper
import ru.neosvet.vestnewage.viewmodel.BookToiler
import ru.neosvet.vestnewage.viewmodel.basic.*
import ru.neosvet.vestnewage.service.LoaderService
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.activity.MarkerActivity
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.basic.Tip
import ru.neosvet.vestnewage.view.basic.select
import ru.neosvet.vestnewage.view.dialog.CustomDialog
import ru.neosvet.vestnewage.view.dialog.DateDialog
import ru.neosvet.vestnewage.view.list.ListAdapter
import java.util.*
import kotlin.math.abs

class BookFragment : NeoFragment(), DateDialog.Result {
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
    private val adBook: ListAdapter by lazy {
        ListAdapter(requireContext())
    }
    private var dateDialog: DateDialog? = null
    private var alertRnd: CustomDialog? = null
    private var x = 0
    private var y = 0
    private lateinit var menuRnd: Tip
    private var binding: BookFragmentBinding? = null
    private val toiler: BookToiler
        get() = neotoiler as BookToiler
    private var dialog: String? = ""
    private var notClick = false
    private val helper: BookHelper
        get() = toiler.helper!!
    val hTimer = Handler {
        binding?.tvDate?.setBackgroundResource(R.drawable.card_bg)
        false
    }
    override val title: String
        get() = ""
    private var openedReader = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = BookFragmentBinding.inflate(inflater, container, false).also {
        binding = it
    }.root

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this).get(BookToiler::class.java)

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        if (toiler.helper == null) {
            toiler.init(requireContext())
            arguments?.let {
                toiler.selectedTab = it.getInt(Const.TAB)
                val year = it.getInt(Const.YEAR)
                if (year > 0) {
                    val d = DateUnit.initToday()
                    d.year = year
                    showDatePicker(d)
                }
            }
        }
        setViews()
        initTabs()
        restoreState(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (openedReader) {
            openedReader = false
            act?.updateNew()
        }
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
        if (state != null && toiler.isRun.not()) {
            state.getString(Const.DIALOG)?.let {
                if (it.contains(DIALOG_DATE)) {
                    if (it != DIALOG_DATE) {
                        val d = DateUnit.parse(it.substring(DIALOG_DATE.length))
                        showDatePicker(d)
                    } else
                        showDatePicker(toiler.date)
                } else if (it.length > 1) {
                    dialog = it
                    val m = it.split(Const.AND).toTypedArray()
                    showRndAlert(m[0], m[1], m[2], m[3], m[4].toInt())
                }
            }
        }
        act?.tabLayout?.select(toiler.selectedTab)
    }

    private fun initTabs() = act?.run {
        tabLayout.addTab(tabLayout.newTab().setText(R.string.katreny))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.poslaniya))
        if (toiler.isKatrenTab.not())
            tabLayout.select(toiler.selectedTab)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab) {
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabSelected(tab: TabLayout.Tab) {
                toiler.cancel()
                toiler.selectedTab = tab.position
                toiler.openList(true)
            }
        })
    }

    override fun onChangedState(state: NeoState) {
        when (state) {
            is SuccessBook ->
                setBook(state)
            is MessageState ->
                Lib.showToast(state.message)
            is SuccessRnd -> with(state) {
                dialog = title + Const.AND + link + Const.AND + msg +
                        Const.AND + place + Const.AND + par
                showRndAlert(title, link, msg, place, par)
            }
        }
    }

    private fun setBook(state: SuccessBook) = binding?.run {
        setStatus(false)
        act?.updateNew()
        tvDate.text = state.date
        ivPrev.isEnabled = state.prev
        ivNext.isEnabled = state.next
        adBook.setItems(state.list)
        lvBook.smoothScrollToPosition(0)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setViews() = binding?.run {
        ivPrev.isEnabled = false
        ivNext.isEnabled = false
        menuRnd = Tip(requireContext(), pRnd)
        bRndStih.setOnClickListener {
            menuRnd.hide()
            toiler.getRnd(BookToiler.RndType.STIH)
        }
        bRndPos.setOnClickListener {
            menuRnd.hide()
            toiler.getRnd(BookToiler.RndType.POS)
        }
        bRndKat.setOnClickListener {
            menuRnd.hide()
            toiler.getRnd(BookToiler.RndType.KAT)
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
        lvBook.adapter = adBook
        lvBook.onItemClickListener =
            OnItemClickListener { _, _, pos: Int, _ ->
                if (notClick) return@OnItemClickListener
                if (toiler.isRun) return@OnItemClickListener
                openedReader = true
                openReader(adBook.getItem(pos).link, null)
            }
        lvBook.setOnTouchListener { _, event: MotionEvent ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (animMinFinished) {
                        fabRefresh.startAnimation(anMin)
                        fabRndMenu.startAnimation(anMin)
                        menuRnd.hide()
                    }
                    x = event.getX(0).toInt()
                    y = event.getY(0).toInt()
                }
                MotionEvent.ACTION_UP -> {
                    val x2 = event.getX(0).toInt()
                    val r = abs(x - x2)
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
                    if (animMaxFinished) {
                        fabRefresh.isVisible = true
                        fabRefresh.startAnimation(anMax)
                        fabRndMenu.isVisible = true
                        fabRndMenu.startAnimation(anMax)
                    }
                }
                MotionEvent.ACTION_CANCEL -> if (animMaxFinished) {
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
        tvDate.setOnClickListener { showDatePicker(toiler.date) }
        fabRndMenu.setOnClickListener {
            if (menuRnd.isShow) menuRnd.hide()
            else menuRnd.show()
        }
    }

    private fun openMonth(plus: Boolean) {
        val d = toiler.date
        if (!plus && toiler.isKatrenTab.not()) {
            if (d.month == 1 && d.year == 2016 && helper.isLoadedOtkr().not()) {
                showAlertDownloadOtkr()
                return
            }
        }
        if (plus) d.changeMonth(1)
        else d.changeMonth(-1)
        blinkDate()
        toiler.openList(true)
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
        val builder = AlertDialog.Builder(requireContext(), R.style.NeoDialog)
        builder.setMessage(getString(R.string.alert_download_otkr))
        builder.setNegativeButton(
            getString(R.string.no)
        ) { dialog: DialogInterface, _ -> dialog.dismiss() }
        builder.setPositiveButton(
            getString(R.string.yes)
        ) { _, _ ->
            binding?.ivPrev?.isEnabled = false
            LoaderService.postCommand(
                LoaderService.DOWNLOAD_OTKR, ""
            )
        }
        builder.create().show()
    }

    override fun setStatus(load: Boolean) {
        super.setStatus(load)
        binding?.run {
            val tabHost = act!!.tabLayout.getChildAt(0) as ViewGroup
            if (load) {
                tabHost.getChildAt(0).isEnabled = false
                tabHost.getChildAt(1).isEnabled = false
                tvDate.isEnabled = false
                ivPrev.isEnabled = false
                ivNext.isEnabled = false
                fabRefresh.isVisible = false
                fabRndMenu.isVisible = false
            } else {
                tabHost.getChildAt(0).isEnabled = true
                tabHost.getChildAt(1).isEnabled = true
                tvDate.isEnabled = true
                fabRefresh.isVisible = true
                fabRndMenu.isVisible = true
            }
        }
    }

    private fun showDatePicker(d: DateUnit) {
        dialog = DIALOG_DATE + d
        dateDialog = DateDialog(act, d).apply {
            setResult(this@BookFragment)
            if (toiler.isKatrenTab) {
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

    override fun putDate(date: DateUnit?) {
        dialog = ""
        if (date == null) //cancel
            return
        toiler.date = date
        toiler.openList(true)
    }

    private fun showRndAlert(title: String, link: String, msg: String, place: String, par: Int) {
        alertRnd = CustomDialog(act).apply {
            setTitle(title)
            setMessage(msg)
            setLeftButton(getString(R.string.in_markers)) {
                val marker = Intent(requireContext(), MarkerActivity::class.java)
                marker.putExtra(Const.LINK, link)
                marker.putExtra(DataBase.PARAGRAPH, par + 1)
                startActivity(marker)
                alertRnd?.dismiss()
            }
            setRightButton(getString(R.string.open)) {
                openedReader = true
                openReader(link, place)
                alertRnd?.dismiss()
            }
        }
        alertRnd?.show { dialog = "" }
    }
}