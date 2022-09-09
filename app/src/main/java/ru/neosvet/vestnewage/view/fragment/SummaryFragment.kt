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
import kotlinx.coroutines.flow.collect
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
import ru.neosvet.vestnewage.view.list.paging.PagingAdapter
import ru.neosvet.vestnewage.viewmodel.SummaryToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler

class SummaryFragment : NeoFragment() {
    private var binding: SummaryFragmentBinding? = null
    private val adapter = RecyclerAdapter(this::onItemClick, this::onItemLongClick)
    private val toiler: SummaryToiler
        get() = neotoiler as SummaryToiler
    private var jobList: Job? = null
    override val title: String
        get() = getString(R.string.summary)
    private var openedReader = false

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this).get(SummaryToiler::class.java).apply { init(requireContext()) }

    override fun onDestroyView() {
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
        if (savedInstanceState == null)
            toiler.openList(true)
        else if (toiler.selectedTab == SummaryToiler.TAB_ADD)
            initAddition()
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
                adapter.clear()
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
                val scroll = adapter.itemCount > 0
                adapter.setItems(state.list)
                binding?.run {
                    rvSummary.adapter = adapter
                    if (scroll)
                        rvSummary.smoothScrollToPosition(0)
                }
            }
            is NeoState.LongValue -> binding?.run {
                setUpdateTime(state.value, tvUpdate)
            }
            NeoState.Success -> {
                if (toiler.isRss)
                    act?.updateNew()
                else
                    initAddition()
            }
            NeoState.Ready ->
                act?.hideToast()
            else -> {}
        }
    }

    private fun initAddition() {
        jobList?.cancel()
        binding?.tvUpdate?.setText(R.string.link_to_src)
        val adapter = PagingAdapter(this::onItemClick, this::onItemLongClick, this::finishedList)
        binding?.rvSummary?.adapter = adapter
        jobList = lifecycleScope.launch {
            toiler.paging().collect {
                adapter.submitData(lifecycle, it)
            }
        }
    }

    private fun onItemClick(index: Int, item: ListItem) {
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
                pMenu.menu.add(getString(R.string.link_on_post))
            else {
                val item = pMenu.menu.add(it.first)
                item.intent = Intent().apply { this.action = it.second }
            }
        }
        pMenu.setOnMenuItemClickListener { item: MenuItem ->
            if (item.intent != null)
                Lib.openInApps(item.intent.action, null)
            else
                Lib.openInApps(NetConst.TELEGRAM_URL + post, null)
            true
        }
        pMenu.show()
    }

    private fun onItemLongClick(index: Int, item: ListItem): Boolean {
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

    private fun finishedList() {
        if (toiler.isLoading)
            act?.showStaticToast(getString(R.string.load))
        else
            act?.showToast(getString(R.string.finish_list))
    }
}