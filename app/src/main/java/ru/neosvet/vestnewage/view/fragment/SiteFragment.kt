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
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.data.Section
import ru.neosvet.vestnewage.data.SiteTab
import ru.neosvet.vestnewage.databinding.SiteFragmentBinding
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.utils.*
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.basic.getItemView
import ru.neosvet.vestnewage.view.basic.select
import ru.neosvet.vestnewage.view.list.RecyclerAdapter
import ru.neosvet.vestnewage.viewmodel.SiteToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.ListState
import ru.neosvet.vestnewage.viewmodel.state.NeoState
import ru.neosvet.vestnewage.viewmodel.state.SiteState

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
    private var itemAds: BasicItem? = null
    private var selectedTab = SiteTab.NEWS.value
    private val isDevTab: Boolean
        get() = selectedTab == SiteTab.DEV.value
    private val isNewsTab: Boolean
        get() = selectedTab == SiteTab.NEWS.value
    private val isSiteTab: Boolean
        get() = selectedTab == SiteTab.SITE.value

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
        arguments?.let {
            selectedTab = it.getInt(Const.TAB)
            toiler.setArgument(selectedTab)
            arguments = null
        }
        binding?.tabLayout?.select(selectedTab)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        toiler.setStatus(
            SiteState.Status(
                selectedTab = selectedTab,
                itemAds = itemAds
            )
        )
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

    private fun initTabs() = binding?.run {
        tabLayout.addTab(tabLayout.newTab().setText(R.string.news))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.site))
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab) {
                if (isDevTab)
                    onTabSelected(tab)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabSelected(tab: TabLayout.Tab) {
                if (selectedTab == tab.position) return
                selectedTab = tab.position
                adapter.clear()
                toiler.openList(true, selectedTab)
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

    private fun openMultiLink(links: BasicItem, parent: View) {
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
        if (isSiteTab) {
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

    private fun isAds(index: Int, item: BasicItem): Boolean {
        binding?.run {
            if (isDevTab) {
                if (index == 0) { //back
                    tabLayout.select(SiteTab.NEWS.value)
                    return true
                }
                item.des = ""
                itemAds = item
                AdsUtils.showDialog(requireActivity(), item, this@SiteFragment::closeAds)
                adapter.notifyItemChanged(index)
                return true
            }
            if (isNewsTab && index == 0) {
                selectedTab = SiteTab.DEV.value
                toiler.openList(true, selectedTab)
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
            if (url.contains(Urls.Site)) Lib.openInApps(url, getString(R.string.to_load))
            else Lib.openInApps(url, null)
        } else if (url.contains(".jp") || url.contains(".mp")) {
            Lib.openInApps(Urls.Site + url, null)
        } else openReader(url, null)
    }

    override fun onChangedOtherState(state: NeoState) {
        when (state) {
            is BasicState.NotLoaded ->
                binding?.tvUpdate?.text = getString(R.string.list_no_loaded)

            is SiteState.Status ->
                restoreStatus(state)

            is ListState.Primary -> {
                setStatus(false)
                binding?.run {
                    setUpdateTime(state.time, tvUpdate)
                    rvSite.layoutManager = if (isNewsTab)
                        GridLayoutManager(requireContext(), 1)
                    else
                        GridLayoutManager(requireContext(), ScreenUtils.span)
                    if (state.list.isEmpty())
                        act?.showStaticToast(getString(R.string.empty_site))
                    else
                        act?.hideToast()
                }
                adapter.setItems(state.list)
            }
        }
    }

    private fun restoreStatus(state: SiteState.Status) {
        selectedTab = state.selectedTab
        binding?.tabLayout?.select(selectedTab)
        itemAds = state.itemAds?.also {
            AdsUtils.showDialog(requireActivity(), it, this::closeAds)
        }
    }

    private fun onItemClick(index: Int, item: BasicItem) {
        if (isBlocked) return
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