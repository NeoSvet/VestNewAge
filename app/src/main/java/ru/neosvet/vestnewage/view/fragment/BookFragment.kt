package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
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
import ru.neosvet.vestnewage.databinding.BookFragmentBinding
import ru.neosvet.vestnewage.helper.BookHelper
import ru.neosvet.vestnewage.network.NetConst
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.activity.MarkerActivity
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.basic.select
import ru.neosvet.vestnewage.view.dialog.CustomDialog
import ru.neosvet.vestnewage.view.dialog.DateDialog
import ru.neosvet.vestnewage.view.dialog.DownloadDialog
import ru.neosvet.vestnewage.view.list.RecyclerAdapter
import ru.neosvet.vestnewage.viewmodel.BookToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler

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
    }

    private val adapter: RecyclerAdapter = RecyclerAdapter(this::onItemClick, this::onItemLongClick)
    private var dateDialog: DateDialog? = null
    private var alertRnd: CustomDialog? = null
    private var binding: BookFragmentBinding? = null
    private val toiler: BookToiler
        get() = neotoiler as BookToiler
    private val helper: BookHelper
        get() = toiler.helper!!
    override val title: String
        get() = getString(R.string.book)
    private var openedReader = false
    private var shownDwnDialog = false

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
        if (shownDwnDialog)
            outState.putInt(Const.DIALOG, 0)
        else dateDialog?.let { d ->
            outState.putInt(Const.DIALOG, d.date.timeInDays)
            d.dismiss()
        }
        super.onSaveInstanceState(outState)
    }

    private fun restoreState(state: Bundle?) {
        if (state != null && toiler.isRun.not()) {
            val t = state.getInt(Const.DIALOG, -1)
            if (t > 0) {
                val d = DateUnit.putDays(t)
                showDatePicker(d)
            } else if (t == 0)
                showDownloadDialog()
        }
        binding?.tabLayout?.select(toiler.selectedTab)
        checkChangeTab()
    }

    private fun initTabs() = binding?.run {
        tabLayout.addTab(tabLayout.newTab().setText(R.string.poems))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.epistles))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.doctrine))
        if (toiler.isPoemsTab.not())
            tabLayout.select(toiler.selectedTab)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab) {
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabSelected(tab: TabLayout.Tab) {
                toiler.selectedTab = tab.position
                toiler.openList(true)
                checkChangeTab()
            }
        })
    }

    private fun checkChangeTab() = binding?.run {
        if (toiler.isDoctrineTab) {
            bPrev.isVisible = false
            bNext.isVisible = false
            tvDate.isVisible = false
            tvLink?.let {
                it.isVisible = true
                tvUpdate.isVisible = false
            }
        } else {
            bPrev.isVisible = true
            bNext.isVisible = true
            tvDate.isVisible = true
            tvLink?.let {
                it.isVisible = false
                tvUpdate.isVisible = true
            }
        }
    }

    override fun onChangedOtherState(state: NeoState) {
        when (state) {
            is NeoState.Book ->
                setBook(state)
            is NeoState.LongValue -> binding?.run {
                if (!toiler.isDoctrineTab)
                    setUpdateTime(state.value, tvUpdate)
            }
            is NeoState.ListValue -> binding?.run { //doctrine
                adapter.setItems(state.list)
                rvBook.smoothScrollToPosition(0)
                tvUpdate.text = getString(R.string.link_to_src)
            }
            is NeoState.Success ->
                setStatus(false)
            is NeoState.Message ->
                act?.showToast(state.message)
            is NeoState.Rnd -> with(state) {
                showRndAlert(title, link, msg, place, par)
            }
            else -> {}
        }
    }

    private fun setBook(state: NeoState.Book) = binding?.run {
        act?.run {
            if (status.isVisible) {
                setStatus(false)
                updateNew()
            }
        }
        if (ScreenUtils.isWide)
            tvDate.text = state.date.replace(Const.N, " ")
        else
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
        tvLink?.setOnClickListener {
            Lib.openInApps(NetConst.DOCTRINE_SITE, null)
        } ?: tvUpdate.setOnClickListener {
            if (toiler.isDoctrineTab)
                Lib.openInApps(NetConst.DOCTRINE_SITE, null)
        }
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
        if (!plus && toiler.isPoemsTab.not()) {
            if (d.month == 1 && d.year == 2016 && helper.isLoadedOtkr().not()) {
                showDownloadDialog()
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
        dateDialog = DateDialog(act, d).apply {
            setResult(this@BookFragment)
            if (toiler.isPoemsTab) {
                setMinMonth(2) //feb
            } else { //epistles
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
        alertRnd?.show { neotoiler.clearStates() }
    }

    private fun onItemClick(index: Int, item: ListItem) {
        if (toiler.isRun) return
        openedReader = true
        openReader(item.link, null)
    }

    override fun onAction(title: String) {
        when (title) {
            getString(R.string.refresh) ->
                startLoad()
            getString(R.string.rnd_verse) ->
                toiler.getRnd(BookToiler.RndType.VERSE)
            getString(R.string.rnd_epistle) ->
                toiler.getRnd(BookToiler.RndType.EPISTLE)
            getString(R.string.rnd_poem) ->
                toiler.getRnd(BookToiler.RndType.POEM)
        }
    }

    private fun onItemLongClick(index: Int, item: ListItem): Boolean {
        MarkerActivity.addByPar(
            requireContext(),
            item.link, "", ""
        )
        return true
    }
}