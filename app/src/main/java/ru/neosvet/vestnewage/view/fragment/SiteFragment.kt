package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.AbsListView
import android.widget.AdapterView.OnItemClickListener
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.tabs.TabLayout
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.databinding.SiteFragmentBinding
import ru.neosvet.vestnewage.viewmodel.SiteToiler
import ru.neosvet.vestnewage.viewmodel.basic.LongState
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.basic.SuccessList
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.utils.AdsUtils
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.basic.select
import ru.neosvet.vestnewage.view.list.ListAdapter
import kotlin.math.abs

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
    private val adMain: ListAdapter by lazy {
        ListAdapter(requireContext())
    }
    private val ads: AdsUtils by lazy {
        AdsUtils(act)
    }
    private var x = 0
    private var y = 0
    private var scrollToFirst = false
    private var binding: SiteFragmentBinding? = null
    override val title: String
        get() = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = SiteFragmentBinding.inflate(inflater, container, false).also {
        binding = it
    }.root

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this).get(SiteToiler::class.java)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        act?.fab = binding?.fabRefresh
        setViews()
        initTabs()
        restoreState(savedInstanceState)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onStop() {
        super.onStop()
        ads.close()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (ads.index > -1)
            outState.putInt(Const.ADS, ads.index)
        super.onSaveInstanceState(outState)
    }

    override fun setStatus(load: Boolean) {
        super.setStatus(load)
        binding?.run {
            val tabHost = act!!.tabLayout.getChildAt(0) as ViewGroup
            if (load) {
                tabHost.getChildAt(0).isEnabled = false
                tabHost.getChildAt(1).isEnabled = false
                fabRefresh.isVisible = false
            } else {
                tabHost.getChildAt(0).isEnabled = true
                tabHost.getChildAt(1).isEnabled = true
                fabRefresh.isVisible = true
            }
        }
    }

    private fun restoreState(state: Bundle?) {
        if (state != null) {
            val indexAds = state.getInt(Const.ADS, -1)
            if (indexAds > -1)
                ads.index = indexAds
        } else {
            arguments?.let {
                toiler.selectedTab = it.getInt(Const.TAB)
            }
            when (toiler.selectedTab) {
                SiteToiler.TAB_NEWS -> toiler.openList(true)
                SiteToiler.TAB_SITE -> act?.tabLayout?.select(toiler.selectedTab)
                SiteToiler.TAB_DEV -> toiler.openAds()
            }
        }
    }

    private fun initTabs() = act?.run {
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
                toiler.openList(true)
            }
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setViews() = binding?.run {
        fabRefresh.setOnClickListener { startLoad() }
        lvMain.adapter = adMain
        lvMain.onItemClickListener = OnItemClickListener { _, view: View, pos: Int, _ ->
            if (toiler.isRun) return@OnItemClickListener
            if (isAds(pos)) return@OnItemClickListener
            if (adMain.getItem(pos).hasFewLinks())
                openMultiLink(adMain.getItem(pos), view)
            else
                openSingleLink(adMain.getItem(pos).link)
        }
        lvMain.setOnTouchListener { _, event: MotionEvent ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (animMinFinished) act?.startAnimMin()
                    x = event.getX(0).toInt()
                    y = event.getY(0).toInt()
                }
                MotionEvent.ACTION_UP -> {
                    val x2 = event.getX(0).toInt()
                    val r = abs(x - x2)
                    if (r > (30 * resources.displayMetrics.density).toInt() &&
                        r > abs(y - event.getY(0).toInt())
                    ) act?.run {
                        val t = tabLayout.selectedTabPosition
                        if (x > x2) // next
                            if (t < 1) tabLayout.select(t + 1)
                            else if (x < x2) // prev
                                if (t > 0) tabLayout.select(t - 1)
                    }
                    if (animMaxFinished) act?.startAnimMax()
                }
                MotionEvent.ACTION_CANCEL ->
                    if (animMaxFinished) act?.startAnimMax()
            }
            false
        }
        lvMain.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(absListView: AbsListView, scrollState: Int) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE && scrollToFirst) {
                    if (lvMain.firstVisiblePosition > 0)
                        lvMain.smoothScrollToPosition(0)
                    else scrollToFirst = false
                }
            }

            override fun onScroll(
                absListView: AbsListView, firstVisibleItem: Int,
                visibleItemCount: Int, totalItemCount: Int
            ) {
            }
        })
    }

    private fun openMultiLink(links: ListItem, parent: View) {
        val pMenu = PopupMenu(requireContext(), parent)
        links.headsAndLinks().forEach {
            val item = pMenu.menu.add(it.first)
            item.intent = Intent().apply { this.action = it.second }
        }
        pMenu.setOnMenuItemClickListener { item: MenuItem ->
            item.intent.action?.let {
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
                act?.setFragment(R.id.nav_rss, true)
            else if (link.contains("poems"))
                act?.openBook(link, true)
            else if (link.contains("tolkovaniya") || link.contains("2016"))
                act?.openBook(link, false)
            else if (link.contains("files"))
                Lib.openInApps(link, null)
            else openPage(link)
        } else
            openPage(link)
    }

    private fun isAds(pos: Int): Boolean {
        binding?.run {
            if (toiler.isDevTab) {
                if (pos == 0) { //back
                    act?.tabLayout?.select(SiteToiler.TAB_NEWS)
                    return true
                }
                ads.index = pos
                ads.showAd(
                    adMain.getItem(pos).title,
                    adMain.getItem(pos).link,
                    adMain.getItem(pos).head
                )
                adMain.getItem(pos).des = ""
                adMain.notifyDataSetChanged()
                return true
            }
            if (toiler.isNewsTab && pos == 0) {
                toiler.selectedTab = SiteToiler.TAB_DEV
                toiler.openAds()
                return true
            }
        }
        return false
    }

    private fun openPage(url: String) {
        if (url.contains("http") || url.contains("mailto")) {
            if (url.contains(NeoClient.SITE))
                Lib.openInApps(url, getString(R.string.to_load))
            else
                Lib.openInApps(url, null)
        } else
            openReader(url, null)
    }

    override fun onChangedState(state: NeoState) {
        when (state) {
            is SuccessList -> {
                setStatus(false)
                adMain.setItems(state.list)
                binding?.run {
                    if (lvMain.firstVisiblePosition > 0) {
                        scrollToFirst = true
                        lvMain.smoothScrollToPosition(0)
                    }
                    tvEmptySite.isVisible = state.list.isEmpty()
                }
                if (toiler.isDevTab && ads.index > -1) {
                    ads.showAd(
                        adMain.getItem(ads.index).title,
                        adMain.getItem(ads.index).link,
                        adMain.getItem(ads.index).head
                    )
                }
            }
            is LongState ->
                binding?.fabRefresh?.isVisible = act?.status?.checkTime(state.value) == false
        }
    }
}