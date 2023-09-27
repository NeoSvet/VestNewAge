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
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.data.Section
import ru.neosvet.vestnewage.data.SiteTab
import ru.neosvet.vestnewage.databinding.TabFragmentBinding
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.utils.*
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.basic.getItemView
import ru.neosvet.vestnewage.view.list.RecyclerAdapter
import ru.neosvet.vestnewage.view.list.TabAdapter
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
    private var binding: TabFragmentBinding? = null
    override val title: String
        get() = getString(R.string.news)
    private var itemAds: BasicItem? = null
    private lateinit var tabAdapter: TabAdapter
    private val isDevTab: Boolean
        get() = tabAdapter.selected == SiteTab.DEV.value
    private val isNewsTab: Boolean
        get() = tabAdapter.selected == SiteTab.NEWS.value
    private val isSiteTab: Boolean
        get() = tabAdapter.selected == SiteTab.SITE.value

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = TabFragmentBinding.inflate(inflater, container, false).also {
        binding = it
    }.root

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this)[SiteToiler::class.java]

    override fun onViewCreated(savedInstanceState: Bundle?) {
        setViews()
        initTabs()
        arguments?.let {
            val tab = it.getInt(Const.TAB, 0)
            tabAdapter.select(tab)
            toiler.setArgument(tab)
            arguments = null
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        toiler.setStatus(
            SiteState.Status(
                selectedTab = tabAdapter.selected,
                itemAds = itemAds
            )
        )
        super.onSaveInstanceState(outState)
    }

    override fun setStatus(load: Boolean) {
        super.setStatus(load)
        tabAdapter.isBlocked = isBlocked
    }

    private fun initTabs() = binding?.run {
        val tabs = listOf(
            getString(R.string.news),
            getString(R.string.site),
            getString(R.string.news_dev)
        )
        tabAdapter = TabAdapter(btnPrevTab, btnNextTab, true) {
            rvTab.isEnabled = false
            adapter.clear()
            toiler.openList(true, it)
        }
        tabAdapter.setItems(tabs)
        rvTab.adapter = tabAdapter
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setViews() = binding?.run {
        rvList.adapter = adapter
        setListEvents(rvList, false)
    }

    override fun swipeLeft() {
        val t = tabAdapter.selected
        if (t < 2) {
            tabAdapter.select(t + 1)
            toiler.openList(true, tabAdapter.selected)
        }
    }

    override fun swipeRight() {
        val t = tabAdapter.selected
        if (t > 0) {
            tabAdapter.select(t - 1)
            toiler.openList(true, tabAdapter.selected)
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
                binding?.run {
                    setUpdateTime(state.time, tvUpdate)
                    rvList.layoutManager =
                        GridLayoutManager(requireContext(), if (isNewsTab) 1 else ScreenUtils.span)
                    if (state.list.isEmpty())
                        act?.showStaticToast(getString(R.string.empty_site))
                    else act?.hideToast()
                }
                adapter.setItems(state.list)
                setStatus(false)
            }
        }
    }

    private fun restoreStatus(state: SiteState.Status) {
        tabAdapter.select(state.selectedTab)
        itemAds = state.itemAds?.also {
            AdsUtils.showDialog(requireActivity(), it, this::closeAds)
        }
    }

    private fun onItemClick(index: Int, item: BasicItem) {
        if (isBlocked) return
        if (isDevTab) {
            item.des = ""
            itemAds = item
            AdsUtils.showDialog(requireActivity(), item, this@SiteFragment::closeAds)
            adapter.notifyItemChanged(index)
            return
        }
        if (item.hasFewLinks())
            openMultiLink(item, binding!!.rvList.getItemView(index))
        else
            openSingleLink(item.link)
    }

    override fun onAction(title: String) {
        startLoad()
    }
}