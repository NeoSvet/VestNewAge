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
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.BrowserActivity
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.list.HomeAdapter
import ru.neosvet.vestnewage.view.list.HomeHolder
import ru.neosvet.vestnewage.view.list.MoveHelper
import ru.neosvet.vestnewage.viewmodel.HomeToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.HomeState
import ru.neosvet.vestnewage.viewmodel.state.ListState
import ru.neosvet.vestnewage.viewmodel.state.NeoState

class HomeFragment : NeoFragment(), HomeAdapter.Events {
    private lateinit var adapter: HomeAdapter
    private val toiler: HomeToiler
        get() = neotoiler as HomeToiler
    override val title: String
        get() = getString(R.string.home_screen)
    private var openedReader = false
    private val mover: MoveHelper by lazy {
        MoveHelper { i, up ->
            if (up) {
                adapter.moveUp(i)
                toiler.moveUp(i)
            } else {
                adapter.moveDown(i)
                toiler.moveDown(i)
            }
        }
    }
    private lateinit var rvList: RecyclerView

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this)[HomeToiler::class.java]

    override fun onDestroyView() {
        act?.unlockHead()
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.list_fragment, container, false).also {
        initView(it)
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
    }

    override fun onSaveInstanceState(outState: Bundle) {
        toiler.setStatus(HomeState.Status(openedReader))
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (openedReader) {
            toiler.updateJournal()
            openedReader = false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initView(container: View) {
        rvList = container.findViewById(R.id.rvList) as RecyclerView
        rvList.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = resources.getDimension(R.dimen.content_margin_bottom).toInt()
        }
    }

    override fun setStatus(load: Boolean) {
        if (!load && adapter.loadingIndex > -1) //if error
            adapter.finishLoading()
    }

    override fun onChangedOtherState(state: NeoState) {
        when (state) {
            is HomeState.Primary ->
                initPrimary(state)

            is ListState.Update<*> ->
                adapter.update(state.index, state.item as HomeItem)

            is HomeState.Loading ->
                adapter.startLoading(state.index)

            is HomeState.Status -> if (state.openedReader)
                toiler.updateJournal()

            BasicState.Success ->
                act?.updateNew()
        }
    }

    private fun initPrimary(state: HomeState.Primary) {
        if (state.isEditor) {
            act?.setAction(R.drawable.ic_ok)
            mover.attach(rvList)
            rvList.layoutManager = GridLayoutManager(requireContext(), 1)
        } else {
            act?.setAction(R.drawable.star)
            mover.detach()
            rvList.layoutManager = GridLayoutManager(requireContext(), ScreenUtils.span)
        }
        adapter = HomeAdapter(
            events = this, isEditor = state.isEditor,
            items = state.list, menu = state.menu
        )
        rvList.adapter = adapter
    }

    override fun onItemClick(type: HomeItem.Type, action: HomeHolder.Action) {
        when (type) {
            HomeItem.Type.SUMMARY -> when {
                action == HomeHolder.Action.REFRESH -> toiler.refreshSummary()
                action == HomeHolder.Action.TITLE || toiler.linkSummary.isEmpty() ->
                    act?.setSection(Section.SUMMARY, true)

                action == HomeHolder.Action.SUBTITLE -> openReader(toiler.linkSummary)
            }

            HomeItem.Type.ADDITION -> when (action) {
                HomeHolder.Action.TITLE -> Lib.openInApps(Urls.TelegramUrl, null)
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

            HomeItem.Type.INFO -> {
                val link = if (action == HomeHolder.Action.SUBTITLE)
                    Urls.PRECEPT_LINK else Urls.PROM_LINK
                openReader(link)
            }

            HomeItem.Type.JOURNAL ->
                if (action == HomeHolder.Action.TITLE || toiler.linkJournal.isEmpty())
                    act?.setSection(Section.JOURNAL, true)
                else openReader(toiler.linkJournal)

            else -> {} //HomeItem.Type.MENU, HomeItem.Type.DIV
        }
    }

    override fun onItemMove(holder: RecyclerView.ViewHolder) {
        mover.startMove(holder)
    }

    private fun openReader(link: String) {
        openedReader = true
        BrowserActivity.openReader(link, null)
    }

    override fun onMenuClick(section: Section) {
        if (adapter.isEditor) {
            // TODO edit menu
            return
        }
        if (section == Section.MENU) toiler.edit()
        else act?.setSection(section, true)
    }

    override fun onAction(title: String) {
        if (title == getString(R.string.edit))
            toiler.edit()
        else toiler.save()
    }

    override fun onBackPressed() = if (adapter.isEditor) {
        toiler.restore()
        false
    } else true
}