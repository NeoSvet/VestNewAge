package ru.neosvet.vestnewage.fragment

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
import ru.neosvet.vestnewage.list.ListAdapter
import ru.neosvet.vestnewage.model.BookModel
import ru.neosvet.vestnewage.model.basic.*
import ru.neosvet.vestnewage.service.LoaderService
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
    private val model: BookModel
        get() = neomodel as BookModel
    private var dialog: String? = ""
    private var notClick = false
    private val helper: BookHelper
        get() = model.helper!!
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

    override fun initViewModel(): NeoViewModel =
        ViewModelProvider(this).get(BookModel::class.java)

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        if (model.helper == null) {
            model.init(requireContext())
            arguments?.let {
                model.selectedTab = it.getInt(Const.TAB)
                val year = it.getInt(Const.YEAR)
                if (year > 0) {
                    val d = DateHelper.initToday()
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
        if (state != null && model.isRun.not()) {
            state.getString(Const.DIALOG)?.let {
                if (it.contains(DIALOG_DATE)) {
                    if (it != DIALOG_DATE) {
                        val d = DateHelper.parse(it.substring(DIALOG_DATE.length))
                        showDatePicker(d)
                    } else
                        showDatePicker(model.date)
                } else if (it.length > 1) {
                    dialog = it
                    val m = it.split(Const.AND).toTypedArray()
                    showRndAlert(m[0], m[1], m[2], m[3], m[4].toInt())
                }
            }
        }
        act?.tabLayout?.select(model.selectedTab)
    }

    private fun initTabs() = act?.run {
        tabLayout.addTab(tabLayout.newTab().setText(R.string.katreny))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.poslaniya))
        if (model.isKatrenTab.not())
            tabLayout.select(model.selectedTab)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab) {
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabSelected(tab: TabLayout.Tab) {
                model.cancel()
                model.selectedTab = tab.position
                model.openList(true)
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
        lvBook.adapter = adBook
        lvBook.onItemClickListener =
            OnItemClickListener { _, _, pos: Int, _ ->
                if (notClick) return@OnItemClickListener
                if (model.isRun) return@OnItemClickListener
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
        tvDate.setOnClickListener { showDatePicker(model.date) }
        fabRndMenu.setOnClickListener {
            if (menuRnd.isShow) menuRnd.hide()
            else menuRnd.show()
        }
    }

    private fun openMonth(plus: Boolean) {
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

    private fun showDatePicker(d: DateHelper) {
        dialog = DIALOG_DATE + d
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