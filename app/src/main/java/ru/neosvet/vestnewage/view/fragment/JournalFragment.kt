package ru.neosvet.vestnewage.view.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.databinding.TabFragmentBinding
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.activity.MarkerActivity.Companion.addByPar
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.basic.NeoScrollBar
import ru.neosvet.vestnewage.view.basic.onSizeChange
import ru.neosvet.vestnewage.view.list.paging.NeoPaging
import ru.neosvet.vestnewage.view.list.paging.PagingAdapter
import ru.neosvet.vestnewage.viewmodel.JournalToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.JournalState
import ru.neosvet.vestnewage.viewmodel.state.ListState
import ru.neosvet.vestnewage.viewmodel.state.NeoState

class JournalFragment : NeoFragment(), PagingAdapter.Parent, NeoScrollBar.Host {
    private val toiler: JournalToiler
        get() = neotoiler as JournalToiler
    private val adapter: PagingAdapter by lazy {
        PagingAdapter(this)
    }

    override val title: String
        get() = getString(R.string.journal)
    private var jobList: Job? = null
    private var isUserScroll = true
    private var firstPosition = -1
    private var binding: TabFragmentBinding? = null

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this)[JournalToiler::class.java]

    override fun onDestroyView() {
        jobList?.cancel()
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = TabFragmentBinding.inflate(inflater, container, false).also {
        binding = it
    }.root

    override fun onViewCreated(savedInstanceState: Bundle?) {
        binding?.run {
            rvList.layoutManager = GridLayoutManager(requireContext(), ScreenUtils.span)
            rvList.adapter = adapter
            setListEvents(rvList, false)
            tvUpdate.isVisible = false
        }
        if (savedInstanceState == null)
            initTabs(0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        toiler.setStatus(
            JournalState.Status(
                firstPosition = adapter.firstPosition,
                tab = binding?.pTab?.selectedIndex ?: 0
            )
        )
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (adapter.itemCount == 0 && firstPosition > -1)
            startPaging(firstPosition / NeoPaging.ON_PAGE)
    }

    private fun initTabs(tab: Int) = binding?.run {
        val tabs = listOf(
            getString(R.string.opened),
            getString(R.string.rnd)
        )
        pTab.setOnChangeListener {
            firstPosition = 0
            rvList.adapter = null
            adapter.submitData(lifecycle, PagingData.empty())
            toiler.openList(it)
        }
        pTab.setItems(tabs, tab)
        rvList.onSizeChange {
            if (ScreenUtils.isLand) pTab.limitedWidth()
        }
    }

    override fun swipeLeft() {
        binding?.pTab?.change(true)
    }

    override fun swipeRight() {
        binding?.pTab?.change(false)
    }

    private fun startPaging(page: Int) = binding?.run {
        jobList?.cancel()
        rvList.adapter = null
        jobList = lifecycleScope.launch {
            toiler.paging(page, adapter).collect {
                adapter.submitData(lifecycle, it)
            }
        }
        rvList.adapter = adapter
    }

    override fun onItemClick(index: Int, item: BasicItem) {
        var s = item.des
        firstPosition = adapter.firstPosition
        adapter.submitData(lifecycle, PagingData.empty())
        if (s.contains(getString(R.string.rnd_verse))) {
            val i = s.indexOf(Const.N, s.indexOf(getString(R.string.rnd_verse))) + 1
            s = s.substring(i)
            openReader(item.link, s)
        } else openReader(item.link, null)
    }

    override fun onItemLongClick(index: Int, item: BasicItem): Boolean {
        var des = item.des
        var par = ""
        var i = des.indexOf(getString(R.string.rnd_verse))
        if (i > -1 && i < des.lastIndexOf(Const.N)) {
            par = des.substring(des.indexOf(Const.N, i) + 1)
            i = des.indexOf("«")
            des = des.substring(i, des.indexOf(Const.N, i) - 1)
        } else if (des.contains("«")) {
            des = des.substring(des.indexOf("«"))
        } else des = des.substring(des.indexOf("(") + 1, des.indexOf(")"))
        addByPar(
            requireContext(),
            item.link, par, des
        )
        return true
    }

    override fun onChangePage(page: Int) {
        if (page > 0)
            act?.lockHead()
        isUserScroll = false
        act?.setScrollBar(page)
        isUserScroll = true
    }

    override fun onFinishList(endList: Boolean) {
        if (toiler.isLoading)
            act?.showToast(getString(R.string.load))
        else act?.let {
            it.showToast(getString(R.string.finish_list))
            if (endList) act?.setScrollBar(-1)
            else it.unlockHead()
        }
    }

    override fun onChangedInsets(insets: android.graphics.Insets) {
        binding?.run { rvList.updatePadding(bottom = App.CONTENT_BOTTOM_INDENT) }
    }

    override fun onChangedOtherState(state: NeoState) {
        when (state) {
            is BasicState.Message ->
                act?.showScrollTip(state.message)

            BasicState.Ready ->
                act?.hideToast()

            is ListState.Paging ->
                openList(state.max)

            is JournalState.Status ->
                restoreStatus(state)

            BasicState.Empty -> act?.run {
                showStaticToast(getString(R.string.list_empty))
                setAction(0)
            }
        }
    }

    private fun restoreStatus(state: JournalState.Status) {
        firstPosition = state.firstPosition
        initTabs(state.tab)
    }

    private fun openList(max: Int) {
        act?.let {
            it.setAction(R.drawable.ic_clear)
            if (max > NeoPaging.ON_PAGE)
                it.initScrollBar(max / NeoPaging.ON_PAGE + 1, this)
            else it.initScrollBar(0, null)
        }
        if (firstPosition < 1) startPaging(0)
        else startPaging(firstPosition / NeoPaging.ON_PAGE)
    }

    override fun onScrolled(value: Int) {
        if (isUserScroll) {
            firstPosition = value * NeoPaging.ON_PAGE
            startPaging(value)
        }
    }

    override fun onPreviewScroll(value: Int) {
        if (isUserScroll)
            toiler.getTimeOn(value * NeoPaging.ON_PAGE)
    }

    override fun onAction(title: String) {
        toiler.clear()
        adapter.submitData(lifecycle, PagingData.empty())
    }
}