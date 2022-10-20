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
import ru.neosvet.vestnewage.data.Section
import ru.neosvet.vestnewage.databinding.SiteFragmentBinding
import ru.neosvet.vestnewage.network.NetConst
import ru.neosvet.vestnewage.utils.*
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.basic.getItemView
import ru.neosvet.vestnewage.view.basic.select
import ru.neosvet.vestnewage.view.list.RecyclerAdapter
import ru.neosvet.vestnewage.viewmodel.SiteToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler

class SiteFragment : NeoFragment() {
    companion object {
        fun newInstance(tab: Int): SiteFragment {
            val fragment = SiteFragment()
            val args = Bundle()
            args.putInt(Const.TAB, tab)
            fragment.arguments = args
            return fragment
        }
    }

    private val toiler: SiteToiler
        get() = neotoiler as SiteToiler
    private val adapter: RecyclerAdapter = RecyclerAdapter(this::onItemClick)
    private var binding: SiteFragmentBinding? = null
    override val title: String
        get() = getString(R.string.news)
    private var itemAds: ListItem? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = SiteFragmentBinding.inflate(inflater, container, false).also {
        binding = it
    }.root

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this)[SiteToiler::class.java]

    override fun onViewCreated(savedInstanceState: Bundle?) {
        setViews()
        initTabs()
        restoreState(savedInstanceState)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        itemAds?.let {
            outState.putStringArray(Const.ADS, it.main)
        }
        super.onSaveInstanceState(outState)
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

    private fun restoreState(state: Bundle?) {
        if (state != null) state.getStringArray(Const.ADS)?.let {
            itemAds = ListItem(it)
            AdsUtils.showDialog(requireActivity(), itemAds!!, this::closeAds)
        } else {
            arguments?.let {
                toiler.selectedTab = it.getInt(Const.TAB)
                binding?.tabLayout?.select(toiler.selectedTab)
            }
            when (toiler.selectedTab) {
                SiteToiler.TAB_DEV -> toiler.openAds()
                else -> toiler.openList(true)
            }
        }
    }

    private fun initTabs() = binding?.run {
        tabLayout.addTab(tabLayout.newTab().setText(R.string.news))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.site))
        if (toiler.selectedTab == SiteToiler.TAB_SITE)
            tabLayout.select(toiler.selectedTab)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab) {
                if (toiler.isDevTab)
                    onTabSelected(tab)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabSelected(tab: TabLayout.Tab) {
                toiler.selectedTab = tab.position
                adapter.clear()
                toiler.openList(true)
            }
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setViews() = binding?.run {
        rvSite.adapter = adapter
        setListEvents(rvSite, false)
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

    private fun openMultiLink(links: ListItem, parent: View) {
        val pMenu = PopupMenu(requireContext(), parent)
        links.headsAndLinks().forEach {
            val item = pMenu.menu.add(it.first)
            item.intent = Intent().apply { this.action = it.second }
        }
        pMenu.setOnMenuItemClickListener { item: MenuItem ->
            item.intent?.action?.let {
                openPage(it)
            }
            true
        }
        pMenu.show()
    }

    private fun openSingleLink(link: String) {
        if (link == "#" || link == "@") return
        if (toiler.isSiteTab) {
            if (link.contains("rss"))
                act?.setSection(Section.SUMMARY, true)
            else if (link.isPoem)
                act?.openBook(link, true)
            else if (link.contains("tolkovaniya") || link.contains("2016"))
                act?.openBook(link, false)
            else if (link.contains("files"))
                Lib.openInApps(link, null)
            else openPage(link)
        } else
            openPage(link)
    }

    private fun isAds(index: Int, item: ListItem): Boolean {
        binding?.run {
            if (toiler.isDevTab) {
                if (index == 0) { //back
                    tabLayout.select(SiteToiler.TAB_NEWS)
                    return true
                }
                item.des = ""
                itemAds = item
                AdsUtils.showDialog(requireActivity(), item, this@SiteFragment::closeAds)
                adapter.notifyItemChanged(index)
                return true
            }
            if (toiler.isNewsTab && index == 0) {
                toiler.selectedTab = SiteToiler.TAB_DEV
                toiler.openAds()
                return true
            }
        }
        return false
    }

    private fun closeAds() = itemAds?.let {
        toiler.readAds(it)
        itemAds = null
    }

    private fun openPage(url: String) {
        if (url.contains("http") || url.contains("mailto")) {
            if (url.contains(NetConst.SITE))
                Lib.openInApps(url, getString(R.string.to_load))
            else
                Lib.openInApps(url, null)
        } else
            openReader(url, null)
    }

    override fun onChangedOtherState(state: NeoState) {
        if (state is NeoState.ListValue) {
            setStatus(false)
            binding?.run {
                rvSite.layoutManager = if (toiler.isNewsTab)
                    GridLayoutManager(requireContext(), 1)
                else
                    GridLayoutManager(requireContext(), ScreenUtils.span)
                if (state.list.isEmpty())
                    act?.showStaticToast(getString(R.string.empty_site))
                else
                    act?.hideToast()
            }
            adapter.setItems(state.list)
        } else if (state is NeoState.LongValue) binding?.run {
            setUpdateTime(state.value, tvUpdate)
        }
    }

    private fun onItemClick(index: Int, item: ListItem) {
        if (toiler.isRun) return
        if (isAds(index, item)) return
        if (item.hasFewLinks())
            openMultiLink(item, binding!!.rvSite.getItemView(index))
        else
            openSingleLink(item.link)
    }

    override fun onAction(title: String) {
        startLoad()
    }
}