package ru.neosvet.vestnewage.view.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.MenuItem
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.utils.ScreenUtils.isTabletLand
import ru.neosvet.vestnewage.view.activity.MainActivity
import ru.neosvet.vestnewage.view.list.MenuAdapter

class MenuFragment : Fragment() {
    companion object {
        private const val MAX = 12
        private val mMenu = listOf(
            R.id.nav_new, R.id.nav_rss, R.id.nav_site, R.id.nav_calendar,
            R.id.nav_book, R.id.nav_search, R.id.nav_marker, R.id.nav_journal,
            R.id.nav_cabinet, R.id.nav_settings, R.id.nav_help
        )
        private var iSelect = -1
    }

    private var act: MainActivity? = null
    private var lastNewId = 0
    private lateinit var rvMenu: RecyclerView
    private val adapter = MenuAdapter(this::onItemClick)
    private var isFullScreen = false

    override fun onAttach(context: Context) {
        act = activity as MainActivity?
        super.onAttach(context)
    }

    override fun onDestroyView() {
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
        val ivHeadMenu = container.findViewById(R.id.ivHeadMenu) as View
        if (isTabletLand) {
            ivHeadMenu.setOnClickListener {
                Lib.openInApps(
                    NeoClient.SITE.substring(0, NeoClient.SITE.length - 1), null
                )
            }
            if (MainActivity.isCountInMenu)
                act!!.setProm(container.findViewById(R.id.tvPromTimeInMenu))
            initList(0)
        } else {
            isFullScreen = true
            act!!.title = getString(R.string.app_name)
            ivHeadMenu.isVisible = false
            initList(1)
        }
    }

    private fun initList(n: Int) {
        rvMenu.layoutManager = GridLayoutManager(
            requireContext(), if (isFullScreen)
                ScreenUtils.span else 1
        )
        rvMenu.adapter = adapter
        val mImage = intArrayOf(
            R.drawable.ic_download, R.drawable.ic_0, R.drawable.ic_rss, R.drawable.ic_main,
            R.drawable.ic_calendar, R.drawable.ic_book, R.drawable.ic_search, R.drawable.ic_marker,
            R.drawable.ic_journal, R.drawable.ic_cabinet, R.drawable.ic_settings, R.drawable.ic_help
        )
        val mTitle = intArrayOf(
            R.string.download_title, R.string.new_section, R.string.rss,
            R.string.news, R.string.calendar, R.string.book, R.string.search, R.string.markers,
            R.string.journal, R.string.cabinet, R.string.settings, R.string.help
        )
        var i = n
        while (i < mImage.size) {
            adapter.addItem(mImage[i], getString(mTitle[i]))
            i++
        }
        if (iSelect > -1)
            adapter.select(getPos(iSelect))
    }

    fun setSelect(id: Int) {
        if (isFullScreen) return
        for (i in mMenu.indices) {
            if (id == mMenu[i]) {
                iSelect = getPos(i)
                if (adapter.itemCount > i)
                    adapter.select(iSelect)
                return
            }
        }
    }

    private fun getPos(i: Int): Int {
        return if (isFullScreen) i else i + 1
    }

    fun setNew(newId: Int) {
        if (adapter.itemCount > 0 && newId != lastNewId) {
            lastNewId = newId
            adapter.changeIcon(newId, getPos(0))
        }
    }

    private fun onItemClick(index: Int, item: MenuItem) {
        if (index == 0 && !isFullScreen) {
            act?.showDownloadMenu()
            return
        }
        if (item.isSelect) return
        val i = if (isFullScreen) {
            index
        } else {
            iSelect = index
            index - 1
        }
        act?.setFragment(mMenu[i], false)
    }
}