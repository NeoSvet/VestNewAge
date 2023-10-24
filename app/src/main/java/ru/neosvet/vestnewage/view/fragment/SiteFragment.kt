package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
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
import ru.neosvet.vestnewage.view.list.BasicAdapter
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
    private val adapter = BasicAdapter(this::onItemClick)
    private var binding: TabFragmentBinding? = null
    override val title: String
        get() = getString(R.string.news)

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
        val tab = if (savedInstanceState == null)
            arguments?.getInt(Const.TAB, 0) ?: 0
        else 0
        toiler.setArgument(tab)
        initTabs(tab)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        toiler.setStatus(
            SiteState.Status(
                selectedTab = binding?.pTab?.selectedIndex ?: 0
            )
        )
        super.onSaveInstanceState(outState)
    }

    override fun setStatus(load: Boolean) {
        super.setStatus(load)
        binding?.pTab?.isBlocked = isBlocked
    }

    private fun initTabs(tab: Int) = binding?.run {
        val tabs = listOf(
            getString(R.string.news),
            getString(R.string.site),
            getString(R.string.news_dev)
        )
        pTab.setOnChangeListener {
            adapter.clear()
            toiler.openList(true, it)
        }
        pTab.setItems(tabs, tab)
        if (ScreenUtils.isLand) pTab.limitedWidth(lifecycleScope)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setViews() = binding?.run {
        rvList.adapter = adapter
        setListEvents(rvList, false)
    }

    override fun swipeLeft() {
        binding?.pTab?.change(true)
    }

    override fun swipeRight() {
        binding?.pTab?.change(false)
    }

    private fun openSingleLink(link: String) {
        if (link == "#") return
        if (link.contains("rss"))
            act?.setSection(Section.SUMMARY, true)
        else if (link.isPoem)
            act?.openBook(link, true)
        else if (link.contains("tolkovaniya") || link.contains("2016"))
            act?.openBook(link, false)
        else openPage(link)
    }

    private fun openPage(url: String) {
        when {
            url.contains("mailto") -> Urls.openInApps(url)
            url.contains("http") -> Urls.openInBrowser(url)
            url.contains(".jp") || url.contains(".mp") -> Urls.openInBrowser(Urls.Site + url)
            else -> openReader(url, null)
        }
    }

    override fun onChangedOtherState(state: NeoState) {
        when (state) {
            is BasicState.NotLoaded ->
                binding?.tvUpdate?.text = getString(R.string.list_no_loaded)

            is SiteState.Status ->
                restoreStatus(state)

            is ListState.Update<*> ->
                adapter.update(state.index, state.item as BasicItem)

            is ListState.Primary -> {
                binding?.run {
                    setUpdateTime(state.time, tvUpdate)
                    val span = if (pTab.selectedStart) 1 else ScreenUtils.span
                    rvList.layoutManager = GridLayoutManager(requireContext(), span)
                    if (state.list.isEmpty())
                        act?.showStaticToast(getString(R.string.list_empty))
                    else act?.hideToast()
                }
                adapter.setItems(state.list)
                setStatus(false)
            }
        }
    }

    private fun restoreStatus(state: SiteState.Status) {
        binding?.pTab?.selectedIndex = state.selectedTab
    }

    private fun onItemClick(index: Int, item: BasicItem) {
        if (isBlocked) return
        binding?.run {
            when (pTab.selectedIndex) {
                SiteTab.NEWS.value -> if (item.link.length > 1) {
                    if (item.link.contains(":") && item.head.isNotEmpty())
                        adapter.openLinksFor(index)
                    else openPage(item.link)
                }

                SiteTab.SITE.value -> if (item.hasFewLinks())
                    adapter.openLinksFor(index)
                else if (item.des.isNotEmpty()) openPage(item.link)
                else openSingleLink(item.link)

                SiteTab.DEV.value -> {
                    if (item.title == getString(R.string.mark_read)) {
                        toiler.allMarkAsRead()
                        return
                    }
                    toiler.markAsRead(index, item)
                    if (item.hasFewLinks()) {
                        adapter.openLinksFor(index)
                        adapter.notifyItemChanged(index)
                    } else if (item.link.isNotEmpty()) openPage(item.link)
                }
            }
        }
    }

    override fun onAction(title: String) {
        startLoad()
    }
}