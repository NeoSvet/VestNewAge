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
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.data.SummaryTab
import ru.neosvet.vestnewage.databinding.SummaryFragmentBinding
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.activity.MarkerActivity
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.basic.getItemView
import ru.neosvet.vestnewage.view.basic.select
import ru.neosvet.vestnewage.view.list.RecyclerAdapter
import ru.neosvet.vestnewage.view.list.paging.NeoPaging
import ru.neosvet.vestnewage.view.list.paging.PagingAdapter
import ru.neosvet.vestnewage.viewmodel.SummaryToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.ListState
import ru.neosvet.vestnewage.viewmodel.state.NeoState
import ru.neosvet.vestnewage.viewmodel.state.SummaryState

class SummaryFragment : NeoFragment(), PagingAdapter.Parent {
    companion object {
        fun newInstance(tab: Int): SummaryFragment =
            SummaryFragment().apply {
                arguments = Bundle().apply {
                    putInt(Const.TAB, tab)
                }
            }
    }

    private var binding: SummaryFragmentBinding? = null
    private val adRecycler = RecyclerAdapter(this::onItemClick, this::onItemLongClick)
    private lateinit var adPaging: PagingAdapter
    private val toiler: SummaryToiler
        get() = neotoiler as SummaryToiler
    private var jobList: Job? = null
    override val title: String
        get() = getString(R.string.summary)
    private var openedReader = false
    private var isUserScroll = true
    private var firstPosition = -1
    private var selectedTab = SummaryTab.RSS.value

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
    ) = SummaryFragmentBinding.inflate(inflater, container, false).also {
        binding = it
    }.root

    override fun onViewCreated(savedInstanceState: Bundle?) {
        setViews()
        initTabs()
        arguments?.let {
            selectedTab = it.getInt(Const.TAB, 0)
            binding?.tabLayout?.select(selectedTab)
            toiler.setArgument(selectedTab)
            arguments = null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        firstPosition = if (selectedTab == SummaryTab.ADDITION.value)
            adPaging.firstPosition
        else -1
        toiler.setStatus(
            SummaryState.Status(
                selectedTab = selectedTab,
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
        binding?.run {
            val tabHost = tabLayout.getChildAt(0) as ViewGroup
            if (load) {
                act?.initScrollBar(0, null)
                tabHost.getChildAt(0).isEnabled = false
                tabHost.getChildAt(1).isEnabled = false
            } else {
                act?.hideToast()
                tabHost.getChildAt(0).isEnabled = true
                tabHost.getChildAt(1).isEnabled = true
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setViews() = binding?.run {
        rvSummary.layoutManager = GridLayoutManager(requireContext(), ScreenUtils.span)
        setListEvents(rvSummary, false)
        tvUpdate.setOnClickListener {
            if (selectedTab == SummaryTab.ADDITION.value)
                Lib.openInApps(Urls.TelegramUrl, null)
        }
    }

    override fun swipeLeft() {
        binding?.run {
            val t = tabLayout.selectedTabPosition
            if (t < 1) tabLayout.select(t + 1)
        }
    }

    override fun swipeRight() {
        binding?.run {
            val t = tabLayout.selectedTabPosition
            if (t > 0) tabLayout.select(t - 1)
        }
    }

    private fun initTabs() = binding?.run {
        tabLayout.addTab(tabLayout.newTab().setText(R.string.summary))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.additionally))
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab) {}

            override fun onTabUnselected(tab: TabLayout.Tab?) {}

            override fun onTabSelected(tab: TabLayout.Tab) {
                if (selectedTab == tab.position) return
                selectedTab = tab.position
                if (selectedTab == SummaryTab.RSS.value) {
                    act?.initScrollBar(0, null)
                    act?.unlockHead()
                }
                adRecycler.clear()
                toiler.openList(true, selectedTab)
            }
        })
    }

    override fun onChangedOtherState(state: NeoState) {
        setStatus(false)
        when (state) {
            is ListState.Primary ->
                initRss(state)

            is ListState.Paging ->
                initAddition(state.max)

            is SummaryState.Status ->
                restoreStatus(state)

            BasicState.Success ->
                act?.updateNew()

            BasicState.Ready ->
                act?.hideToast()
        }
    }

    private fun restoreStatus(state: SummaryState.Status) {
        selectedTab = state.selectedTab
        firstPosition = state.firstPosition
        binding?.tabLayout?.select(selectedTab)
    }

    private fun initRss(state: ListState.Primary) {
        act?.updateNew()
        jobList?.cancel()
        val scroll = adRecycler.itemCount > 0
        adRecycler.setItems(state.list)
        binding?.run {
            setUpdateTime(state.time, tvUpdate)
            rvSummary.adapter = adRecycler
            if (scroll)
                rvSummary.smoothScrollToPosition(0)
        }
    }

    private fun initAddition(max: Int) {
        binding?.tvUpdate?.setText(R.string.link_to_src)
        adPaging = PagingAdapter(this)
        adPaging.withTime = true
        binding?.rvSummary?.adapter = adPaging
        act?.initScrollBar(max / NeoPaging.ON_PAGE, this::onScroll)
        if (firstPosition == -1 || max == 0) {
            firstPosition = 0
            startPaging(0)
        } else if (firstPosition != adPaging.firstPosition) {
            startPaging(firstPosition / NeoPaging.ON_PAGE)
            if (firstPosition % NeoPaging.ON_PAGE > 0)
                adPaging.scrollTo(firstPosition)
        }
    }

    private fun onScroll(value: Int) {
        if (isUserScroll) {
            firstPosition = value * NeoPaging.ON_PAGE
            startPaging(value)
        }
    }

    private fun startPaging(page: Int) {
        jobList?.cancel()
        jobList = lifecycleScope.launch {
            toiler.paging(page, adPaging).collect {
                adPaging.submitData(lifecycle, it)
            }
        }
    }

    override fun onItemClick(index: Int, item: BasicItem) {
        if (isBlocked) return
        if (selectedTab == SummaryTab.RSS.value) {
            openedReader = true
            openReader(item.link, null)
        } else {
            firstPosition = index
            if (item.hasFewLinks())
                openMultiLink(item, binding!!.rvSummary.getItemView(index))
            else
                Lib.openInApps(Urls.TelegramUrl + item.link, null)
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
            Lib.openInApps(item.intent?.action ?: (Urls.TelegramUrl + post), null)
            true
        }
        pMenu.show()
    }

    override fun onItemLongClick(index: Int, item: BasicItem): Boolean {
        if (selectedTab == SummaryTab.RSS.value) {
            MarkerActivity.addByPar(
                requireContext(),
                item.link, "", item.des.substring(item.des.indexOf(Const.N))
            )
        } else {
            Lib.copyAddress(Urls.TelegramUrl + item.link)
        }
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

    override fun onFinishList() {
        act?.let {
            if (toiler.isLoading)
                it.showStaticToast(getString(R.string.load))
            else {
                it.showToast(getString(R.string.finish_list))
                it.unlockHead()
            }
        }
    }
}