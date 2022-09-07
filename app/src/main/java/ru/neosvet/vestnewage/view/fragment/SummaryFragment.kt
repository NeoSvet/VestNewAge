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
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.tabs.TabLayout
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
import ru.neosvet.vestnewage.view.basic.select
import ru.neosvet.vestnewage.view.list.RecyclerAdapter
import ru.neosvet.vestnewage.viewmodel.SummaryToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler

class SummaryFragment : NeoFragment() {
    private var binding: SummaryFragmentBinding? = null
    private val adapter: RecyclerAdapter = RecyclerAdapter(this::onItemClick, this::onItemLongClick)
    private val toiler: SummaryToiler
        get() = neotoiler as SummaryToiler
    override val title: String
        get() = getString(R.string.summary)
    private var openedReader = false

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this).get(SummaryToiler::class.java).apply { init(requireContext()) }

    override fun onDestroyView() {
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
    }

    override fun onResume() {
        super.onResume()
        if (openedReader) {
            openedReader = false
            act?.updateNew()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setViews() = binding?.run {
        rvSummary.layoutManager = GridLayoutManager(requireContext(), ScreenUtils.span)
        rvSummary.adapter = adapter
        setListEvents(rvSummary)
        tvUpdate.setOnClickListener {
            if (toiler.isRss.not())
                Lib.openInApps(NetConst.TELEGRAM_URL, null)
        }
    }

    private fun initTabs() = binding?.run {
        tabLayout.addTab(tabLayout.newTab().setText(R.string.summary))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.additionally))
        if (toiler.selectedTab == SummaryToiler.TAB_RSS)
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
            act?.updateNew()
        }
        if (state is NeoState.ListValue) {
            val scroll = adapter.itemCount > 0
            adapter.setItems(state.list)
            binding?.run {
                if (scroll) rvSummary.smoothScrollToPosition(0)
                if (toiler.isRss.not())
                    tvUpdate.setText(R.string.link_to_src)
            }
        } else if (state is NeoState.LongValue) binding?.run {
            setUpdateTime(state.value, tvUpdate)
        }
    }

    private fun onItemClick(index: Int, item: ListItem) {
        if (toiler.isRun) return
        if (toiler.isRss) {
            openedReader = true
            openReader(item.link, null)
        } else if (item.hasFewLinks()) {
            openMultiLink(
                item,
                binding!!.rvSummary.findViewHolderForAdapterPosition(index)!!.itemView
            )
        } else
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
            if (item.intent.action != null)
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
}