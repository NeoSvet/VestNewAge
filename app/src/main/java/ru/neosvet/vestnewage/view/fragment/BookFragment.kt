package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.data.BookRnd
import ru.neosvet.vestnewage.data.BookTab
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.databinding.BookFragmentBinding
import ru.neosvet.vestnewage.helper.DateHelper
import ru.neosvet.vestnewage.network.Urls
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
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.BookState
import ru.neosvet.vestnewage.viewmodel.state.NeoState

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
    override val title: String
        get() = getString(R.string.book)
    private var openedReader = false
    private var shownDwnDialog = false
    private var selectedTab = BookTab.POEMS.value
    private var date = DateUnit.initToday().apply { day = 1 }
    private var linkToSrc = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = BookFragmentBinding.inflate(inflater, container, false).also {
        binding = it
    }.root

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this)[BookToiler::class.java]

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        setViews()
        initTabs()
        arguments?.let {
            selectedTab = it.getInt(Const.TAB)
            binding?.tabLayout?.select(selectedTab)
            checkChangeTab()
            toiler.setArgument(selectedTab)
            val year = it.getInt(Const.YEAR)
            if (year > 0) {
                val d = DateUnit.initToday()
                d.year = year
                showDatePicker(d)
            }
            arguments = null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        toiler.setStatus(
            BookState.Status(
                selectedTab = selectedTab,
                dateDialog = dateDialog?.date,
                shownDwnDialog = shownDwnDialog
            )
        )
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (openedReader) {
            openedReader = false
            act?.updateNew()
        }
    }

    private fun initTabs() = binding?.run {
        tabLayout.addTab(tabLayout.newTab().setText(R.string.poems))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.epistles))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.doctrine))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab) {
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabSelected(tab: TabLayout.Tab) {
                if (selectedTab == tab.position) return
                selectedTab = tab.position
                toiler.openList(true, date, selectedTab)
                checkChangeTab()
            }
        })
    }

    private fun checkChangeTab() = binding?.run {
        if (selectedTab == BookTab.DOCTRINE.value) {
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
            is BasicState.Success ->
                setStatus(false)

            is BasicState.Message ->
                act?.showToast(state.message)

            is BasicState.NotLoaded ->
                binding?.tvUpdate?.text = getString(R.string.list_no_loaded)

            is BasicState.Empty ->
                binding?.tvUpdate?.text = getString(R.string.empty_list)

            is BookState.Status ->
                restoreStatus(state)

            is BookState.Primary -> binding?.run {
                linkToSrc = ""
                adapter.clear()
                setUpdateTime(state.time, tvUpdate)
                date = state.date
                if (ScreenUtils.isLand)
                    tvDate.text = date.calendarString.replace(Const.N, " ")
                else
                    tvDate.text = date.calendarString
                bPrev.isEnabled = state.prev
                bNext.isEnabled = state.next //doctrine
                adapter.setItems(state.list)
                rvBook.smoothScrollToPosition(0)
                if (selectedTab == BookTab.DOCTRINE.value)
                    tvUpdate.text = getString(R.string.link_to_src)
            }

            is BookState.Book -> binding?.run {
                linkToSrc = state.linkToSrc
                adapter.clear()
                adapter.setItems(state.list)
                if (state.list.isEmpty()) {
                    tvUpdate.text = getString(R.string.list_no_loaded)
                    return
                }
                tvUpdate.text = getString(R.string.link_to_src)
                rvBook.smoothScrollToPosition(0)
            }

            is BookState.Rnd ->
                showRndAlert(state)
        }
    }

    private fun restoreStatus(state: BookState.Status) {
        selectedTab = state.selectedTab
        binding?.tabLayout?.select(selectedTab)
        checkChangeTab()
        if (state.shownDwnDialog)
            showDownloadDialog()
        state.dateDialog?.let {
            showDatePicker(it)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setViews() = binding?.run {
        rvBook.layoutManager = GridLayoutManager(requireContext(), ScreenUtils.span)
        rvBook.adapter = adapter
        setListEvents(rvBook, false)
        bPrev.setOnClickListener { openMonth(false) }
        bNext.setOnClickListener { openMonth(true) }
        tvDate.setOnClickListener { showDatePicker(date) }
        tvLink?.setOnClickListener {
            Lib.openInApps(Urls.DoctrineSite, null)
        } ?: tvUpdate.setOnClickListener {
            if (linkToSrc.isNotEmpty())
                Lib.openInApps(linkToSrc, null)
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
        if (!plus && selectedTab != BookTab.POEMS.value) {
            if (date.timeInDays == DateHelper.MIN_DAYS_NEW_BOOK && !DateHelper.isLoadedOtkr()) {
                showDownloadDialog()
                return
            }
        }
        if (plus) date.changeMonth(1)
        else date.changeMonth(-1)
        blinkDate()
        toiler.openList(true, date)
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
            tabHost.children.forEach {
                it.isEnabled = !load
            }
            if (load) {
                tvDate.isEnabled = false
                bPrev.isEnabled = false
                bNext.isEnabled = false
            } else {
                tvDate.isEnabled = true
            }
        }
    }

    private fun showDatePicker(d: DateUnit) {
        dateDialog = DateDialog(requireActivity(), d, this).apply {
            if (selectedTab == BookTab.POEMS.value) {
                minMonth = 2 //feb
                minYear = 2016
            } else { //epistles
                maxMonth = 9 //sep
                maxYear = 2016
            }
            setOnDismissListener { dateDialog = null }
            show()
        }
    }

    override fun putDate(date: DateUnit?) {
        if (date != null)
            toiler.openList(true, date)
    }

    private fun showRndAlert(state: BookState.Rnd) {
        alertRnd = CustomDialog(act).apply {
            setTitle(title)
            setMessage(state.msg)
            setLeftButton(getString(R.string.in_markers)) {
                val marker = Intent(requireContext(), MarkerActivity::class.java)
                marker.putExtra(Const.LINK, state.link)
                marker.putExtra(DataBase.PARAGRAPH, state.par + 1)
                startActivity(marker)
                alertRnd?.dismiss()
            }
            setRightButton(getString(R.string.open)) {
                openReader(state.link, state.place)
                act?.updateNew()
                alertRnd?.dismiss()
            }
        }
        alertRnd?.show { toiler.clearStates() }
    }

    private fun onItemClick(index: Int, item: BasicItem) {
        if (isBlocked) return
        openedReader = true
        openReader(item.link, null)
    }

    override fun onAction(title: String) {
        when (title) {
            getString(R.string.refresh) ->
                startLoad()

            getString(R.string.rnd_verse) ->
                toiler.getRnd(BookRnd.VERSE)

            getString(R.string.rnd_epistle) ->
                toiler.getRnd(BookRnd.EPISTLE)

            getString(R.string.rnd_poem) ->
                toiler.getRnd(BookRnd.POEM)
        }
    }

    private fun onItemLongClick(index: Int, item: BasicItem): Boolean {
        MarkerActivity.addByPar(
            requireContext(),
            item.link, "", ""
        )
        return true
    }
}