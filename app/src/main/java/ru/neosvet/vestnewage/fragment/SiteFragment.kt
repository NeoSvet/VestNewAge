package ru.neosvet.vestnewage.fragment

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
import ru.neosvet.ui.NeoFragment
import ru.neosvet.ui.select
import ru.neosvet.utils.Const
import ru.neosvet.utils.Lib
import ru.neosvet.utils.NeoClient
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.databinding.SiteFragmentBinding
import ru.neosvet.vestnewage.helpers.DevadsHelper
import ru.neosvet.vestnewage.list.ListAdapter
import ru.neosvet.vestnewage.list.ListItem
import ru.neosvet.vestnewage.model.SiteModel
import ru.neosvet.vestnewage.model.basic.CheckTime
import ru.neosvet.vestnewage.model.basic.NeoState
import ru.neosvet.vestnewage.model.basic.NeoViewModel
import ru.neosvet.vestnewage.model.basic.SuccessList
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

    private val model: SiteModel
        get() = neomodel as SiteModel
    private val adMain: ListAdapter by lazy {
        ListAdapter(requireContext())
    }
    private var ads: DevadsHelper? = null
    private var x = 0
    private var y = 0
    private var scrollToFirst = false
    private var binding: SiteFragmentBinding? = null
    override val title: String
        get() = getString(R.string.news)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = SiteFragmentBinding.inflate(inflater, container, false).also {
        binding = it
    }.root

    override fun initViewModel(): NeoViewModel =
        ViewModelProvider(this).get(SiteModel::class.java)

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
        ads?.close()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        ads?.let {
            outState.putInt(Const.ADS, it.index)
        }
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
                ads = DevadsHelper(act).apply { index = indexAds }
        } else {
            arguments?.let {
                model.selectedTab = it.getInt(Const.TAB)
            }
            when (model.selectedTab) {
                SiteModel.TAB_NEWS -> model.openList(true)
                SiteModel.TAB_SITE -> act?.tabLayout?.select(model.selectedTab)
                SiteModel.TAB_DEV -> model.openAds()
            }
        }
    }

    private fun initTabs() = act?.run {
        tabLayout.addTab(tabLayout.newTab().setText(R.string.news))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.site))
        if (model.selectedTab == SiteModel.TAB_SITE)
            tabLayout.select(model.selectedTab)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab) {
                if (model.isDevTab)
                    onTabSelected(tab)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabSelected(tab: TabLayout.Tab) {
                model.selectedTab = tab.position
                model.openList(true)
            }
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setViews() = binding?.run {
        fabRefresh.setOnClickListener { startLoad() }
        lvMain.adapter = adMain
        lvMain.onItemClickListener = OnItemClickListener { _, view: View, pos: Int, _ ->
            if (model.isRun) return@OnItemClickListener
            if (isAds(pos)) return@OnItemClickListener
            if (adMain.getItem(pos).hasFewLinks()) {
                openMultiLink(adMain.getItem(pos), view)
            } else {
                openSingleLink(adMain.getItem(pos).link)
            }
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
                        if (x > x2) { // next
                            if (t < 1) tabLayout.select(t + 1)
                        } else if (x < x2) { // prev
                            if (t > 0) tabLayout.select(t - 1)
                        }
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
        if (model.isSiteTab) {
            if (link.contains("rss")) {
                act?.setFragment(R.id.nav_rss, true)
            } else if (link.contains("poems")) {
                act?.openBook(link, true)
            } else if (link.contains("tolkovaniya") || link.contains("2016")) {
                act?.openBook(link, false)
            } else if (link.contains("files") && !link.contains("http")) {
                openPage(NeoClient.SITE + link)
            } else openPage(link)
        } else {
            openPage(link)
        }
    }

    private fun isAds(pos: Int): Boolean {
        binding?.run {
            if (model.isDevTab) {
                if (pos == 0) { //back
                    act?.tabLayout?.select(SiteModel.TAB_NEWS)
                    return true
                }
                if (ads == null) ads = DevadsHelper(act)
                ads?.run {
                    index = pos
                    showAd(
                        adMain.getItem(pos).title,
                        adMain.getItem(pos).link,
                        adMain.getItem(pos).head
                    )
                }
                adMain.getItem(pos).des = ""
                adMain.notifyDataSetChanged()
                return true
            }
            if (model.isNewsTab && pos == 0) {
                model.selectedTab = SiteModel.TAB_DEV
                model.openAds()
                return true
            }
        }
        return false
    }

    private fun openPage(url: String) {
        if (url.contains("http") || url.contains("mailto")) {
            if (url.contains(NeoClient.SITE)) {
                Lib.openInApps(url, getString(R.string.to_load))
            } else {
                Lib.openInApps(url, null)
            }
        } else {
            openReader(url, null)
        }
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
                if (model.isDevTab) ads?.run {
                    if (index > -1)
                        showAd(
                            adMain.getItem(index).title,
                            adMain.getItem(index).link,
                            adMain.getItem(index).head
                        )
                }
            }
            is CheckTime ->
                binding?.fabRefresh?.isVisible = act?.status?.checkTime(state.sec) == false
        }
    }
}