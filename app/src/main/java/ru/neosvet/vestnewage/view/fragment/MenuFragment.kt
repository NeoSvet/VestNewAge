package ru.neosvet.vestnewage.view.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.MenuItem
import ru.neosvet.vestnewage.data.Section
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.utils.ScreenUtils.isTabletLand
import ru.neosvet.vestnewage.view.activity.MainActivity
import ru.neosvet.vestnewage.view.list.MenuAdapter
import ru.neosvet.vestnewage.view.list.ScrollHelper
import ru.neosvet.vestnewage.view.list.TouchHelper

class MenuFragment : Fragment() {
    companion object {
        private const val MAX = 12
        private val mMenu = listOf(
            Section.NEW, Section.SUMMARY, Section.SITE, Section.CALENDAR,
            Section.BOOK, Section.SEARCH, Section.MARKERS, Section.JOURNAL,
            Section.CABINET, Section.SETTINGS, Section.HELP
        )
        private var iSelect = -1
    }

    private var act: MainActivity? = null
    private var lastNewId = 0
    private lateinit var rvMenu: RecyclerView
    private val adapter = MenuAdapter(this::onItemClick)
    private var isFullScreen = false
    private var scroll: ScrollHelper? = null

    override fun onAttach(context: Context) {
        act = activity as MainActivity?
        super.onAttach(context)
    }

    override fun onDestroyView() {
        scroll?.deAttach()
        act = null
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.menu_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView(view)
        if (iSelect > -1)
            adapter.select(iSelect)
    }

    override fun onResume() {
        super.onResume()
        setNew(act!!.newId)
        act?.setFrMenu(this)
    }

    private fun initView(container: View) {
        rvMenu = container.findViewById(R.id.rvMenu)
        if (isTabletLand.not()) {
            isFullScreen = true
            act!!.title = getString(R.string.app_name)
        }
        initList()
    }

    private fun initList() {
        rvMenu.layoutManager = GridLayoutManager(
            requireContext(), if (isFullScreen)
                ScreenUtils.span else 1
        )
        rvMenu.adapter = adapter
        val mImage = intArrayOf(
            R.drawable.ic_0, R.drawable.ic_summary, R.drawable.ic_site,
            R.drawable.ic_calendar, R.drawable.ic_book, R.drawable.ic_search, R.drawable.ic_marker,
            R.drawable.ic_journal, R.drawable.ic_cabinet, R.drawable.ic_settings, R.drawable.ic_help
        )
        val mTitle = intArrayOf(
            R.string.new_section, R.string.summary, R.string.news,
            R.string.calendar, R.string.book, R.string.search, R.string.markers,
            R.string.journal, R.string.cabinet, R.string.settings, R.string.help
        )
        var i = 0
        while (i < mImage.size) {
            adapter.addItem(mImage[i], getString(mTitle[i]))
            i++
        }
        if (iSelect > -1)
            adapter.select(iSelect)
        if (isFullScreen.not()) return
        scroll = ScrollHelper {
            when (it) {
                ScrollHelper.Events.SCROLL_END ->
                    act?.hideBottomArea()
                ScrollHelper.Events.SCROLL_START ->
                    act?.showBottomArea()
            }
        }.apply { attach(rvMenu) }
        val touch = TouchHelper(true) {
            act?.hideBottomArea()
        }
        touch.attach(rvMenu)
    }

    fun setSelect(section: Section) {
        if (isFullScreen) return
        for (i in mMenu.indices) {
            if (section == mMenu[i]) {
                iSelect = i
                if (adapter.itemCount > i)
                    adapter.select(iSelect)
                return
            }
        }
    }

    fun setNew(newId: Int) {
        if (adapter.itemCount > 0 && newId != lastNewId) {
            lastNewId = newId
            adapter.changeIcon(newId, 0)
        }
    }

    private fun onItemClick(index: Int, item: MenuItem) {
        if (item.isSelect) return
        if (isFullScreen.not())
            iSelect = index
        act?.setSection(mMenu[index], false)
    }
}