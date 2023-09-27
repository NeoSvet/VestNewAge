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
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.data.BookRnd
import ru.neosvet.vestnewage.data.BookTab
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.databinding.BookFragmentBinding
import ru.neosvet.vestnewage.helper.DateHelper
import ru.neosvet.vestnewage.service.LoaderService
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.activity.MarkerActivity
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.dialog.CustomDialog
import ru.neosvet.vestnewage.view.dialog.DownloadDialog
import ru.neosvet.vestnewage.view.list.RecyclerAdapter
import ru.neosvet.vestnewage.viewmodel.BookToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.BookState
import ru.neosvet.vestnewage.viewmodel.state.NeoState

class BookFragment : NeoFragment() {

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

    private val adapter = RecyclerAdapter(this::onItemClick, this::onItemLongClick)
    private var alertRnd: CustomDialog? = null
    private var binding: BookFragmentBinding? = null
    private val toiler: BookToiler
        get() = neotoiler as BookToiler
    override val title: String
        get() = getString(R.string.book)
    private var openedReader = false
    private var shownDwnDialog = false
    private var linkToSrc = ""
    private val hasDatePicker: Boolean
        get() = binding?.pTab?.selectedIndex != BookTab.DOCTRINE.value


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
        var tab = 0
        if (savedInstanceState == null) {
            arguments?.let {
                tab = it.getInt(Const.TAB)
                val year = it.getInt(Const.YEAR, 0)
                toiler.setArgument(tab, year)
            }
        }
        initTabs(tab)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        toiler.setStatus(
            BookState.Status(
                selectedTab = binding?.pTab?.selectedIndex ?: -1,
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

    private fun initTabs(tab: Int) = binding?.run {
        val tabs = listOf(
            getString(R.string.poems),
            getString(R.string.epistles),
            getString(R.string.doctrine_creator)
        )
        pTab.setOnChangeListener {
            toiler.openList(tab = it)
            checkChangeTab()
        }
        pTab.setItems(tabs, tab)
        checkChangeTab()
        if (ScreenUtils.isLand) pTab.limitedWidth(lifecycleScope)

        pMonth.setOnChangeListener {
            toiler.openList(month = it)
        }
        pYear.setOnChangeListener {
            toiler.openList(year = it)
        }
        pMonth.btnPrev.setOnClickListener { openMonth(false) }
        pMonth.btnNext.setOnClickListener { openMonth(true) }
        if (ScreenUtils.isLand) {
            setListEvents(pYear.rvTab, true)
            setListEvents(pMonth.rvTab, true)
        }
        pMonth.setDescription(getString(R.string.to_prev_month), getString(R.string.to_next_month))
        pYear.setDescription(getString(R.string.to_prev_year), getString(R.string.to_next_year))
    }

    private fun checkChangeTab() = binding?.run {
        if (hasDatePicker) {
            rvBook.layoutManager =
                GridLayoutManager(requireContext(), if (ScreenUtils.isTablet) 2 else 1)
            pYear.isVisible = true
            pMonth.isVisible = true
        } else {
            rvBook.layoutManager = GridLayoutManager(requireContext(), ScreenUtils.span)
            pYear.isVisible = false
            pMonth.isVisible = false
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onChangedOtherState(state: NeoState) {
        when (state) {
            is BasicState.Success ->
                setStatus(false)

            is BasicState.Message ->
                act?.showToast(state.message)

            is BasicState.NotLoaded -> {
                adapter.clear()
                binding?.tvUpdate?.text = getString(R.string.list_no_loaded)
            }

            is BasicState.Empty -> {
                adapter.clear()
                binding?.tvUpdate?.text = getString(R.string.empty_list)
            }

            is BookState.Status ->
                restoreStatus(state)

            is BookState.Primary -> binding?.run {
                linkToSrc = ""
                adapter.clear()
                setUpdateTime(state.time, tvUpdate)
                if (ScreenUtils.isLand) tvUpdate.text = state.label + Const.N + tvUpdate.text
                else tvUpdate.text = state.label + ". " + tvUpdate.text
                pMonth.setItems(state.months, state.selected.y)
                pYear.setItems(state.years, state.selected.x)
                adapter.setItems(state.list)
                rvBook.smoothScrollToPosition(0)
                if (pTab.selectedIndex == BookTab.DOCTRINE.value)
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
        binding?.pTab?.selectedIndex = state.selectedTab
        checkChangeTab()
        if (state.shownDwnDialog)
            showDownloadDialog()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setViews() = binding?.run {
        rvBook.layoutManager = GridLayoutManager(requireContext(), 1)
        rvBook.adapter = adapter
        setListEvents(rvBook, false)
        tvUpdate.setOnClickListener {
            if (linkToSrc.isNotEmpty())
                Lib.openInApps(linkToSrc, null)
        }
    }

    override fun swipeLeft() {
        if (hasDatePicker) openMonth(true)
    }

    override fun swipeRight() {
        if (hasDatePicker) openMonth(false)
    }

    private fun openMonth(plus: Boolean) {
        if (isBlocked) return
        binding?.run {
            if (plus) {
                if (pMonth.selectedEnd && pYear.selectedEnd) {
                    toiler.openList(month = 0)
                    return
                }
            } else if (pMonth.selectedStart && pYear.selectedStart) {
                if (pTab.selectedIndex == BookTab.EPISTLES.value &&
                    !DateHelper.isLoadedOtkr() && !LoaderService.isRun
                ) showDownloadDialog()
                else toiler.openList(month = pMonth.count - 1)
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
            pTab.isBlocked = isBlocked
            pYear.isBlocked = isBlocked
            pMonth.isBlocked = isBlocked
        }
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