package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.data.SummaryTab
import ru.neosvet.vestnewage.databinding.TabFragmentBinding
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.activity.MarkerActivity
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.basic.NeoScrollBar
import ru.neosvet.vestnewage.view.basic.getItemView
import ru.neosvet.vestnewage.view.dialog.ShareDialog
import ru.neosvet.vestnewage.view.list.BasicAdapter
import ru.neosvet.vestnewage.view.list.paging.NeoPaging
import ru.neosvet.vestnewage.view.list.paging.PagingAdapter
import ru.neosvet.vestnewage.viewmodel.SummaryToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.ListState
import ru.neosvet.vestnewage.viewmodel.state.NeoState
import ru.neosvet.vestnewage.viewmodel.state.SummaryState

class SummaryFragment : NeoFragment(), PagingAdapter.Parent, NeoScrollBar.Host {
    companion object {
        fun newInstance(tab: Int): SummaryFragment =
            SummaryFragment().apply {
                arguments = Bundle().apply {
                    putInt(Const.TAB, tab)
                }
            }
    }

    private var binding: TabFragmentBinding? = null
    private val adapter = BasicAdapter(this::onItemClick, this::onItemLongClick)
    private lateinit var adPaging: PagingAdapter
    private val toiler: SummaryToiler
        get() = neotoiler as SummaryToiler
    private var jobList: Job? = null
    override val title: String
        get() = getString(R.string.summary)
    private var openedReader = false
    private var isUserScroll = true
    private var firstPosition = -1

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this)[SummaryToiler::class.java]

    override fun onDestroyView() {
        act?.unlockHead()
        jobList?.cancel()
        binding = null
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
        setViews()
        val tab = if (savedInstanceState == null)
            arguments?.getInt(Const.TAB, 0) ?: 0
        else 0
        toiler.setArgument(tab)
        initTabs(tab)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val tab = binding?.pTab?.selectedIndex ?: 0
        firstPosition = if (tab == SummaryTab.ADDITION.value)
            adPaging.firstPosition
        else -1
        toiler.setStatus(
            SummaryState.Status(
                selectedTab = tab,
                firstPosition = firstPosition
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

    override fun setStatus(load: Boolean) {
        super.setStatus(load)
        binding?.pTab?.isBlocked = isBlocked
        if (load) act?.initScrollBar(0, null)
        else act?.hideToast()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setViews() = binding?.run {
        rvList.layoutManager = GridLayoutManager(requireContext(), ScreenUtils.span)
        setListEvents(rvList, false)
        tvUpdate.setOnClickListener {
            if (pTab.selectedIndex == SummaryTab.ADDITION.value)
                Urls.openInApps(Urls.TelegramUrl)
        }
    }

    override fun swipeLeft() {
        binding?.pTab?.change(true)
    }

    override fun swipeRight() {
        binding?.pTab?.change(false)
    }

    private fun initTabs(tab: Int) = binding?.run {
        val tabs = listOf(
            getString(R.string.summary),
            getString(R.string.additionally)
        )
        pTab.setOnChangeListener {
            rvList.adapter = null
            if (it == SummaryTab.RSS.value) {
                act?.initScrollBar(0, null)
                act?.unlockHead()
            }
            toiler.openList(true, it)
        }
        pTab.setItems(tabs, tab)
        if (ScreenUtils.isLand) pTab.limitedWidth(lifecycleScope)
    }

    override fun onChangedOtherState(state: NeoState) {
        when (state) {
            is BasicState.Message ->
                act?.showScrollTip(state.message)

            is ListState.Primary ->
                initRss(state)

            is ListState.Paging ->
                initAddition(state.max)

            is SummaryState.Status ->
                restoreStatus(state)

            BasicState.Success ->
                setStatus(false)

            BasicState.Ready ->
                act?.hideToast()

            is ListState.Update<*> -> {
                val item = state.item as BasicItem
                ShareDialog.newInstance(
                    link = item.link,
                    title = item.title,
                    content = item.des
                ).show(childFragmentManager, null)
            }
        }
    }

    private fun restoreStatus(state: SummaryState.Status) {
        firstPosition = state.firstPosition
        binding?.pTab?.selectedIndex = state.selectedTab
    }

    private fun initRss(state: ListState.Primary) {
        act?.updateNew()
        jobList?.cancel()
        val scroll = adapter.itemCount > 0
        adapter.setItems(state.list)
        binding?.run {
            setUpdateTime(state.time, tvUpdate)
            rvList.adapter = adapter
            if (scroll)
                rvList.smoothScrollToPosition(0)
        }
        setStatus(false)
    }

    private fun initAddition(max: Int) {
        binding?.tvUpdate?.setText(R.string.link_to_src)
        adPaging = PagingAdapter(this)
        adPaging.withTime = true
        binding?.rvList?.adapter = adPaging
        act?.initScrollBar(max / NeoPaging.ON_PAGE + 1, this)
        if (firstPosition < 1) {
            firstPosition = 0
            startPaging(0)
        } else startPaging(firstPosition / NeoPaging.ON_PAGE)
        setStatus(false)
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

    private fun startPaging(page: Int) = binding?.run {
        jobList?.cancel()
        rvList.adapter = null
        jobList = lifecycleScope.launch {
            toiler.paging(page, adPaging).collect {
                adPaging.submitData(lifecycle, it)
            }
        }
        lifecycleScope.launch {
            delay(50)
            rvList.adapter = adPaging
        }
    }

    override fun onItemClick(index: Int, item: BasicItem) {
        if (isBlocked) return
        binding?.run {
            if (pTab.selectedStart) {
                openedReader = true
                openReader(item.link, null)
            } else {
                firstPosition = index
                if (item.hasFewLinks())
                    openMultiLink(item, rvList.getItemView(index))
                else
                    Urls.openInApps(Urls.TelegramUrl + item.link)
            }
        }
    }

    private fun openMultiLink(links: BasicItem, parent: View) {
        val pMenu = PopupMenu(requireContext(), parent)
        val post = links.link
        links.headsAndLinks().forEach {
            if (it.second == post)
                pMenu.menu.add(getString(R.string.open_post))
            else {
                val item = pMenu.menu.add(it.first)
                item.intent = Intent().apply { this.action = it.second }
            }
        }
        pMenu.setOnMenuItemClickListener { item: MenuItem ->
            Urls.openInApps(item.intent?.action ?: (Urls.TelegramUrl + post))
            true
        }
        pMenu.show()
    }

    override fun onItemLongClick(index: Int, item: BasicItem): Boolean {
        if (binding?.pTab?.selectedStart == true) {
            MarkerActivity.addByPar(
                requireContext(),
                item.link, "", item.des.substring(item.des.indexOf(Const.N))
            )
        } else toiler.shareItem(item.head)
        return true
    }

    override fun onAction(title: String) {
        startLoad()
    }

    override fun onChangePage(page: Int) {
        if (page > 0)
            act?.lockHead()
        isUserScroll = false
        act?.setScrollBar(page)
        isUserScroll = true
    }

    override fun onFinishList(endList: Boolean) {
        act?.let {
            if (toiler.isLoading)
                it.showStaticToast(getString(R.string.load))
            else {
                it.showToast(getString(R.string.finish_list))
                if (endList) act?.setScrollBar(-1)
                else it.unlockHead()
            }
        }
    }
}