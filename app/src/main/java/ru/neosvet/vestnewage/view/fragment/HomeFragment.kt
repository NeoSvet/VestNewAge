package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.HomeItem
import ru.neosvet.vestnewage.data.MenuItem
import ru.neosvet.vestnewage.data.Section
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.BrowserActivity
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.dialog.MessageDialog
import ru.neosvet.vestnewage.view.list.HomeAdapter
import ru.neosvet.vestnewage.view.list.MenuAdapter
import ru.neosvet.vestnewage.view.list.helper.HomeListHelper
import ru.neosvet.vestnewage.view.list.holder.HomeHolder
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
    private var initAdapter = false
    private val listHelper: HomeListHelper by lazy {
        HomeListHelper { i, up ->
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
    private lateinit var rvMenu: RecyclerView

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this)[HomeToiler::class.java]

    override fun onDestroyView() {
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.home_fragment, container, false).also {
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
        if (initAdapter)
            adapter.restoreTimer()
    }

    override fun onPause() {
        if (initAdapter)
            adapter.stopTimer()
        super.onPause()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initView(container: View) {
        rvList = container.findViewById(R.id.rv_list)
        rvMenu = container.findViewById(R.id.rv_menu)
        rvMenu.layoutManager = GridLayoutManager(context, 2)
        rvList.post { setIndent() }
    }

    override fun setStatus(load: Boolean) {
        if (!load && initAdapter && adapter.loadingIndex > -1) //if error
            adapter.finishLoading()
    }

    override fun onChangedInsets(insets: android.graphics.Insets) {
        setIndent()
    }

    private fun setIndent() {
        rvMenu.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = App.CONTENT_BOTTOM_INDENT
        }
        rvList.updatePadding(bottom = App.CONTENT_BOTTOM_INDENT)
    }

    override fun onChangedOtherState(state: NeoState) {
        when (state) {
            is HomeState.Primary ->
                initPrimary(state)

            is ListState.Update<*> -> if (initAdapter)
                adapter.update(state.index, state.item as HomeItem)

            is HomeState.Loading -> if (initAdapter) {
                if (adapter.loadingIndex == state.index)
                    adapter.finishLoading() else
                    adapter.startLoading(state.index)
            }

            is HomeState.Status -> if (state.openedReader)
                toiler.updateJournal()

            is HomeState.Menu ->
                initMenuEdit(state.list)

            is HomeState.ChangeHomeItem -> if (initAdapter)
                adapter.changeMenu(state.index, state.section)

            is HomeState.ChangeMainItem ->
                act?.changeMenu(state.index, state.item)

            BasicState.Success ->
                act?.updateNew()
        }
    }

    private fun initMenuEdit(list: List<MenuItem>) {
        val adapter = MenuAdapter { _, item ->
            if (!item.isSelect)
                toiler.editMenuItem(item.title)
            closeEditMenu()
        }
        adapter.setItems(list)
        rvMenu.adapter = adapter
        rvMenu.isVisible = true
    }

    private fun initPrimary(state: HomeState.Primary) {
        if (initAdapter) adapter.stopTimer()
        adapter = HomeAdapter(
            events = this, isEditor = state.isEditor,
            items = state.list, menu = state.menu
        )
        initAdapter = true
        if (state.isEditor) {
            act?.startEditMenu()
            act?.setAction(R.drawable.ic_ok)
            listHelper.attach(rvList)
            rvList.layoutManager = GridLayoutManager(requireContext(), 1)
        } else {
            act?.setAction(R.drawable.star)
            listHelper.detach()
            if (ScreenUtils.span == 2) {
                rvList.layoutManager = GridLayoutManager(requireContext(), 2)
                val i = state.list.indexOfFirst {
                    it.type == HomeItem.Type.MENU
                } + 1
                adapter.isTall = i < state.list.size || i % 2 == 0
            } else
                rvList.layoutManager = GridLayoutManager(requireContext(), 1)
        }
        rvList.adapter = adapter
    }

    override fun onItemClick(type: HomeItem.Type, action: HomeHolder.Action) {
        if (action is HomeHolder.Action.SUBTITLE && action.link.isNotEmpty()) {
            openReader(action.link)
            return
        }

        when (type) {
            HomeItem.Type.SUMMARY -> when (action) {
                HomeHolder.Action.REFRESH -> toiler.refreshSummary()
                else -> act?.setSection(Section.SUMMARY, true)
            }

            HomeItem.Type.ADDITION -> when (action) {
                HomeHolder.Action.TITLE -> Urls.openInApps(Urls.TelegramUrl)
                is HomeHolder.Action.SUBTITLE -> act?.setSection(Section.SUMMARY, true, 1)
                HomeHolder.Action.REFRESH -> toiler.refreshAddition()
            }

            HomeItem.Type.CALENDAR -> when (action) {
                HomeHolder.Action.REFRESH -> toiler.refreshCalendar()
                else -> act?.setSection(Section.CALENDAR, true)
            }

            HomeItem.Type.NEWS -> when (action) {
                HomeHolder.Action.REFRESH -> toiler.refreshNews()
                else -> act?.setSection(Section.SITE, true, toiler.tabNews)
            }

            HomeItem.Type.INFO -> {
                val link = if (action is HomeHolder.Action.SUBTITLE)
                    Urls.PRECEPT_LINK else Urls.PROM_LINK
                openReader(link)
            }

            HomeItem.Type.JOURNAL ->
                act?.setSection(Section.JOURNAL, true)

            HomeItem.Type.HELP -> {
                val alert = MessageDialog(requireActivity())
                alert.setTitle(getString(R.string.help_edit))
                alert.setMessage(getString(R.string.help_edit_home))
                alert.setRightButton(getString(android.R.string.ok)) { alert.dismiss() }
                alert.show(null)
            }

            else -> {} //HomeItem.Type.MENU, HomeItem.Type.DIV
        }
    }

    override fun onItemMove(holder: RecyclerView.ViewHolder) {
        listHelper.startMove(holder)
    }

    private fun openReader(link: String) {
        openedReader = true
        BrowserActivity.openReader(link, null)
    }

    override fun onMenuClick(index: Int, section: Section) {
        if (adapter.isEditor) {
            toiler.editMenu(index, false)
            return
        }
        if (section == Section.HOME) toiler.edit()
        else act?.setSection(section, true)
    }

    override fun onAction(title: String) {
        when {
            rvMenu.isVisible -> closeEditMenu()
            title == getString(R.string.edit) -> toiler.edit()
            else -> {
                toiler.save()
                act?.finishEditMenu()
            }
        }
    }

    private fun closeEditMenu() {
        rvMenu.isVisible = false
        toiler.clearStates()
    }

    override fun onBackPressed(): Boolean {
        when {
            rvMenu.isVisible -> closeEditMenu()
            initAdapter && adapter.isEditor -> {
                toiler.restore()
                act?.finishEditMenu()
            }

            else -> return true
        }
        return false
    }

    fun editMainMenu(index: Int) {
        toiler.editMenu(index, true)
    }
}