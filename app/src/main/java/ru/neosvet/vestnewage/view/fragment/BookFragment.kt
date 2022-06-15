package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.data.Section
import ru.neosvet.vestnewage.databinding.BookFragmentBinding
import ru.neosvet.vestnewage.helper.BookHelper
import ru.neosvet.vestnewage.service.LoaderService
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.activity.MarkerActivity
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.basic.select
import ru.neosvet.vestnewage.view.dialog.CustomDialog
import ru.neosvet.vestnewage.view.dialog.DateDialog
import ru.neosvet.vestnewage.view.list.RecyclerAdapter
import ru.neosvet.vestnewage.viewmodel.BookToiler
import ru.neosvet.vestnewage.viewmodel.basic.*

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

    private val adapter: RecyclerAdapter = RecyclerAdapter(this::onItemClick)
    private var dateDialog: DateDialog? = null
    private var alertRnd: CustomDialog? = null
    private var binding: BookFragmentBinding? = null
    private val toiler: BookToiler
        get() = neotoiler as BookToiler
    private var dialog = ""
    private val helper: BookHelper
        get() = toiler.helper!!
    override val title: String
        get() = getString(R.string.book)
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
        if (dialog == DIALOG_DATE) dateDialog?.let { d ->
            outState.putString(Const.DIALOG, dialog + d.date)
            d.dismiss()
        } else if (dialog.length > 1) {
            outState.putString(Const.DIALOG, dialog)
            alertRnd!!.dismiss()
        }
        super.onSaveInstanceState(outState)
    }

    private fun restoreState(state: Bundle?) {
        if (state != null && toiler.isRun.not()) {
            state.getString(Const.DIALOG)?.let {
                if (it.contains(DIALOG_DATE)) {
                    val d = DateUnit.parse(it.substring(DIALOG_DATE.length))
                    showDatePicker(d)
                } else if (it.length > 1) {
                    dialog = it
                    val m = it.split(Const.AND).toTypedArray()
                    showRndAlert(m[0], m[1], m[2], m[3], m[4].toInt())
                }
            }
        }
        binding?.tabLayout?.select(toiler.selectedTab)
    }

    private fun initTabs() = binding?.run {
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
            else -> {}
        }
    }

    private fun setBook(state: SuccessBook) = binding?.run {
        act?.run {
            if (status.isVisible) setStatus(false)
            updateNew()
        }
        tvDate.text = state.date
        bPrev.isEnabled = state.prev
        bNext.isEnabled = state.next
        adapter.setItems(state.list)
        rvBook.smoothScrollToPosition(0)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setViews() = binding?.run {
        bPrev.isEnabled = false
        bNext.isEnabled = false
        rvBook.layoutManager = GridLayoutManager(requireContext(), ScreenUtils.span)
        rvBook.adapter = adapter
        setListEvents(rvBook, false)
        bPrev.setOnClickListener { openMonth(false) }
        bNext.setOnClickListener { openMonth(true) }
        tvDate.setOnClickListener { showDatePicker(toiler.date) }
    }

    override fun swipeLeft() {
        if (binding?.bNext?.isEnabled == true)
            openMonth(true)
    }

    override fun swipeRight() {
        if (binding?.bPrev?.isEnabled == true)
            openMonth(false)
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

    private fun blinkDate() = binding?.tvDate?.let {
        it.setBackgroundResource(R.drawable.selected)
        lifecycleScope.launch {
            delay(300)
            it.post { it.setBackgroundResource(R.drawable.card_bg) }
        }
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
            binding?.bPrev?.isEnabled = false
            LoaderService.postCommand(
                LoaderService.DOWNLOAD_OTKR, ""
            )
        }
        builder.create().show()
    }

    override fun setStatus(load: Boolean) {
        super.setStatus(load)
        binding?.run {
            val tabHost = tabLayout.getChildAt(0) as ViewGroup
            if (load) {
                tabHost.getChildAt(0).isEnabled = false
                tabHost.getChildAt(1).isEnabled = false
                tvDate.isEnabled = false
                bPrev.isEnabled = false
                bNext.isEnabled = false
            } else {
                tabHost.getChildAt(0).isEnabled = true
                tabHost.getChildAt(1).isEnabled = true
                tvDate.isEnabled = true
            }
        }
    }

    private fun showDatePicker(d: DateUnit) {
        dialog = DIALOG_DATE
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
            setOnDismissListener { dateDialog = null }
            show()
        }
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

    private fun onItemClick(index: Int, item: ListItem) {
        if (toiler.isRun) return
        openedReader = true
        openReader(item.link, null)
    }

    override fun onAction(title: String) {
        when (title) {
            getString(R.string.download_book) ->
                LoaderService.postCommand(
                    LoaderService.DOWNLOAD_IT, Section.BOOK.toString()
                )
            getString(R.string.refresh) ->
                startLoad()
            getString(R.string.rnd_stih) ->
                toiler.getRnd(BookToiler.RndType.STIH)
            getString(R.string.rnd_pos) ->
                toiler.getRnd(BookToiler.RndType.POS)
            getString(R.string.rnd_kat) ->
                toiler.getRnd(BookToiler.RndType.KAT)
        }
    }
}