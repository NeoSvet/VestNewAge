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
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.databinding.SummaryFragmentBinding
import ru.neosvet.vestnewage.network.NetConst
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
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler

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
    private var firstPosition = 0

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this)[SummaryToiler::class.java].apply { init(requireContext()) }

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
        if (savedInstanceState == null) {
            arguments?.let {
                val tab = it.getInt(Const.TAB, 0)
                binding?.tabLayout?.select(tab)
                toiler.selectedTab = tab
            }
            toiler.openList(true)
        } else {
            firstPosition = savedInstanceState.getInt(Const.PLACE, 0)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (toiler.isRss.not()) {
            firstPosition = adPaging.firstPosition
            outState.putInt(Const.PLACE, firstPosition)
        }
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
            if (toiler.isRss.not())
                Lib.openInApps(NetConst.TELEGRAM_URL, null)
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
        tabLayout.select(toiler.selectedTab)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab) {}

            override fun onTabUnselected(tab: TabLayout.Tab?) {}

            override fun onTabSelected(tab: TabLayout.Tab) {
                toiler.selectedTab = tab.position
                if (toiler.isRss) {
                    act?.initScrollBar(0, null)
                    act?.unlockHead()
                }
                adRecycler.clear()
                toiler.openList(true)
            }
        })
    }

    override fun onChangedOtherState(state: NeoState) {
        if (toiler.isRun.not()) {
            setStatus(false)
            if (toiler.isRss) act?.updateNew()
        }
        when (state) {
            is NeoState.ListValue -> {
                jobList?.cancel()
                val scroll = adRecycler.itemCount > 0
                adRecycler.setItems(state.list)
                binding?.run {
                    rvSummary.adapter = adRecycler
                    if (scroll)
                        rvSummary.smoothScrollToPosition(0)
                }
            }
            is NeoState.LongValue -> binding?.run {
                setUpdateTime(state.value, tvUpdate)
            }
            is NeoState.ListState ->
                initAddition(state.index)
            NeoState.Success ->
                act?.updateNew()
            NeoState.Ready ->
                act?.hideToast()
            else -> {}
        }
    }

    private fun initAddition(max: Int) {
        binding?.tvUpdate?.setText(R.string.link_to_src)
        adPaging = PagingAdapter(this)
        binding?.rvSummary?.adapter = adPaging
        act?.initScrollBar(max / NeoPaging.ON_PAGE, this::onScroll)
        if (firstPosition == 0)
            startPaging(0)
        else {
            startPaging(firstPosition / NeoPaging.ON_PAGE)
            if (firstPosition % NeoPaging.ON_PAGE > 0)
                adPaging.scrollTo(firstPosition)
        }
    }

    private fun onScroll(value: Int) {
        if (isUserScroll)
            startPaging(value)
    }

    private fun startPaging(page: Int) {
        jobList?.cancel()
        jobList = lifecycleScope.launch {
            toiler.paging(page, adPaging).collect {
                adPaging.submitData(lifecycle, it)
            }
        }
    }

    override fun onItemClick(index: Int, item: ListItem) {
        if (toiler.isRun) return
        if (toiler.isRss) {
            openedReader = true
            openReader(item.link, null)
        } else if (item.hasFewLinks())
            openMultiLink(item, binding!!.rvSummary.getItemView(index))
        else
            Lib.openInApps(NetConst.TELEGRAM_URL + item.link, null)
    }

    private fun openMultiLink(links: ListItem, parent: View) {
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
            Lib.openInApps(item.intent?.action ?: (NetConst.TELEGRAM_URL + post), null)
            true
        }
        pMenu.show()
    }

    override fun onItemLongClick(index: Int, item: ListItem): Boolean {
        if (toiler.isRss) {
            MarkerActivity.addByPar(
                requireContext(),
                item.link, "", item.des.substring(item.des.indexOf(Const.N))
            )
        } else {
            Lib.copyAddress(NetConst.TELEGRAM_URL + item.link)
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
        if (toiler.isLoading)
            act?.showStaticToast(getString(R.string.load))
        else act?.let {
            it.showToast(getString(R.string.finish_list))
            it.unlockHead()
        }
    }
}