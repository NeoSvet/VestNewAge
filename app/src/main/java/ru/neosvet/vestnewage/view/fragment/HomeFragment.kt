package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.HomeItem
import ru.neosvet.vestnewage.data.Section
import ru.neosvet.vestnewage.network.NetConst
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.BrowserActivity
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.list.HomeAdapter
import ru.neosvet.vestnewage.view.list.HomeHolder
import ru.neosvet.vestnewage.view.list.HomeMenuHolder
import ru.neosvet.vestnewage.viewmodel.HomeToiler
import ru.neosvet.vestnewage.viewmodel.SiteToiler
import ru.neosvet.vestnewage.viewmodel.basic.ListEvent
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler

class HomeFragment : NeoFragment() {
    private val adapter = HomeAdapter(this::onItemClick, this::onMenuClick)
    private val toiler: HomeToiler
        get() = neotoiler as HomeToiler
    override val title: String
        get() = getString(R.string.home_screen)
    private var openedReader = false

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this)[HomeToiler::class.java].apply { init(requireContext()) }

    override fun onDestroyView() {
        act?.unlockHead()
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.list_fragment, container, false)
        initView(view)
        return view
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        toiler.openList()
    }

    override fun onResume() {
        super.onResume()
        if (openedReader)
            toiler.updateJournal()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initView(container: View) {
        val rv = container.findViewById(R.id.rvList) as RecyclerView
        rv.layoutManager = GridLayoutManager(requireContext(), ScreenUtils.span)
        rv.adapter = adapter
        rv.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = resources.getDimension(R.dimen.content_margin_bottom).toInt()
        }
    }

    override fun setStatus(load: Boolean) {
        if (!load && adapter.loadingIndex > -1) //if error
            toiler.openList()
    }

    override fun onChangedOtherState(state: NeoState) {
        when (state) {
            is NeoState.HomeList ->
                adapter.setItems(state.list)
            is NeoState.HomeUpdate ->
                adapter.update(state.item)
            is NeoState.ListState -> {
                if (state.event == ListEvent.RELOAD)
                    adapter.startLoading(state.index)
                else
                    adapter.finishLoading(state.index)
            }
            NeoState.Success ->
                act?.updateNew()
            else -> {}
        }
    }

    fun onItemClick(type: HomeItem.Type, action: HomeHolder.Action) {
        when (type) {
            HomeItem.Type.SUMMARY -> when {
                action == HomeHolder.Action.REFRESH -> toiler.refreshSummary()
                action == HomeHolder.Action.TITLE || toiler.linkSummary.isEmpty() ->
                    act?.setSection(Section.SUMMARY, true)
                action == HomeHolder.Action.SUBTITLE -> openReader(toiler.linkSummary)
            }
            HomeItem.Type.ADDITION -> when (action) {
                HomeHolder.Action.TITLE -> Lib.openInApps(NetConst.TELEGRAM_URL, null)
                HomeHolder.Action.SUBTITLE -> act?.openAddition()
                HomeHolder.Action.REFRESH -> toiler.refreshAddition()
            }
            HomeItem.Type.CALENDAR -> when {
                action == HomeHolder.Action.REFRESH -> toiler.refreshCalendar()
                action == HomeHolder.Action.TITLE || toiler.linkCalendar.isEmpty() ->
                    act?.setSection(Section.CALENDAR, true)
                action == HomeHolder.Action.SUBTITLE -> openReader(toiler.linkCalendar)
            }
            HomeItem.Type.NEWS -> when (action) {
                HomeHolder.Action.REFRESH -> toiler.refreshNews()
                else -> act?.setSection(Section.SITE, true)
            }
            HomeItem.Type.PROM -> {
                val link = if (action == HomeHolder.Action.SUBTITLE)
                    "Vremya-Posyla.html" else Const.PROM_LINK
                BrowserActivity.openReader(link, null)
            }
            HomeItem.Type.JOURNAL ->
                if (action == HomeHolder.Action.TITLE || toiler.linkJournal.isEmpty())
                    act?.setSection(Section.JOURNAL, true)
                else openReader(toiler.linkJournal)
            HomeItem.Type.MENU -> {}
        }
    }

    private fun openReader(link: String) {
        openedReader = true
        BrowserActivity.openReader(link, null)
    }

    private fun onMenuClick(action: HomeMenuHolder.Action) {
        val section = when (action) {
            HomeMenuHolder.Action.BOOK -> Section.BOOK
            HomeMenuHolder.Action.MARKERS -> Section.MARKERS
            HomeMenuHolder.Action.EDIT -> {
                startEdit()
                return
            }
            HomeMenuHolder.Action.SETTINGS -> Section.SETTINGS
        }
        act?.setSection(section, true)
    }

    private fun startEdit() {
        //TODO("Not yet implemented")
        val file1 = Lib.getFile(SiteToiler.NEWS)
        file1.delete()
        val file2 = Lib.getFile(SiteToiler.MAIN)
        file2.copyTo(file1)
    }

    override fun onAction(title: String) {
        startEdit()
    }
}