package ru.neosvet.vestnewage.helper

import android.content.Context
import android.graphics.Point
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.HomeItem
import ru.neosvet.vestnewage.data.MenuItem
import ru.neosvet.vestnewage.data.Section
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class HomeHelper(private val context: Context) {
    companion object {
        private const val MAIN_MENU = "mainmenu.dat"
        private const val HOME_MENU = "homemenu.dat"
        private const val HOME_ITEMS = "homeitems.dat"

        fun getSectionPoint(section: Section, isMain: Boolean): Point {
            val i: Int
            val t: Int
            when (section) {
                Section.CALENDAR -> {
                    i = R.drawable.ic_calendar
                    t = R.string.calendar
                }

                Section.SUMMARY -> {
                    i = R.drawable.ic_summary
                    t = R.string.summary
                }

                Section.BOOK -> {
                    i = R.drawable.ic_book_k
                    t = R.string.poems
                }

                Section.SITE -> {
                    i = R.drawable.ic_site
                    t = R.string.news
                }

                Section.SEARCH -> {
                    i = R.drawable.ic_search
                    t = R.string.search
                }

                Section.MARKERS -> {
                    i = R.drawable.ic_marker
                    t = R.string.markers
                }

                Section.JOURNAL -> {
                    i = R.drawable.ic_journal
                    t = R.string.journal
                }

                Section.CABINET -> {
                    i = R.drawable.ic_cabinet
                    t = R.string.cabinet
                }

                Section.HOME -> if (isMain) {
                    i = R.drawable.ic_home
                    t = R.string.home_screen
                } else {
                    i = R.drawable.ic_edit
                    t = R.string.edit
                }

                Section.SETTINGS -> {
                    i = R.drawable.ic_settings
                    t = R.string.settings
                }

                Section.EPISTLES -> {
                    i = R.drawable.ic_book_p
                    t = R.string.epistles
                }

                Section.DOCTRINE -> {
                    i = R.drawable.ic_book_dc
                    t = R.string.doctrine_creator
                }

                Section.HOLY_RUS -> {
                    i = R.drawable.ic_book_sr
                    t = R.string.holy_rus
                }

                Section.HELP -> {
                    i = R.drawable.ic_help
                    t = R.string.help
                }

                else -> { //Section.NEW, Section.MENU
                    i = -1
                    t = -1
                }
            }
            return Point(i, t)
        }
    }

    private val listTitle = listOf(
        context.getString(R.string.edit), context.getString(R.string.summary),
        context.getString(R.string.news), context.getString(R.string.calendar),
        context.getString(R.string.poems), context.getString(R.string.search),
        context.getString(R.string.markers), context.getString(R.string.journal),
        context.getString(R.string.cabinet), context.getString(R.string.settings),
        context.getString(R.string.epistles), context.getString(R.string.doctrine_creator),
        context.getString(R.string.holy_rus), context.getString(R.string.help)
    )
    private val listSection: List<Section> by lazy {
        listOf(
            Section.HOME, Section.SUMMARY, Section.SITE, Section.CALENDAR,
            Section.BOOK, Section.SEARCH, Section.MARKERS, Section.JOURNAL,
            Section.CABINET, Section.SETTINGS, Section.EPISTLES,
            Section.DOCTRINE, Section.HOLY_RUS, Section.HELP
        )
    }
    private val listIcon: List<Int> by lazy {
        listOf(
            R.drawable.ic_edit, R.drawable.ic_summary, R.drawable.ic_site, R.drawable.ic_calendar,
            R.drawable.ic_book_k, R.drawable.ic_search, R.drawable.ic_marker, R.drawable.ic_journal,
            R.drawable.ic_cabinet, R.drawable.ic_settings, R.drawable.ic_book_p,
            R.drawable.ic_book_dc, R.drawable.ic_book_sr, R.drawable.ic_help
        )
    }
    var isMain = false
    private val alterTitle = context.getString(R.string.home_screen)

    fun getItem(section: Section): MenuItem {
        val point = getSectionPoint(section, isMain)
        return MenuItem(point.x, context.getString(point.y))
    }

    fun loadMenu(isMain: Boolean): List<Section> {
        val name = if (isMain) MAIN_MENU else HOME_MENU
        val menu = mutableListOf<Section>()
        try {
            val stream = DataInputStream(
                BufferedInputStream(context.openFileInput(name))
            )
            while (stream.available() > 0) {
                val item = when (stream.readInt()) {
                    Section.SEARCH.value -> Section.SEARCH
                    Section.CALENDAR.value -> Section.CALENDAR
                    Section.SUMMARY.value -> Section.SUMMARY
                    Section.NEW.value -> Section.NEW
                    Section.BOOK.value -> Section.BOOK
                    Section.SITE.value -> Section.SITE
                    Section.MARKERS.value -> Section.MARKERS
                    Section.JOURNAL.value -> Section.JOURNAL
                    Section.CABINET.value -> Section.CABINET
                    Section.HELP.value -> Section.HELP
                    Section.SETTINGS.value -> Section.SETTINGS
                    Section.HOME.value -> Section.HOME
                    Section.EPISTLES.value -> Section.EPISTLES
                    Section.DOCTRINE.value -> Section.DOCTRINE
                    Section.HOLY_RUS.value -> Section.HOLY_RUS
                    else -> Section.MENU
                }
                menu.add(item)
            }
            stream.close()
        } catch (ignore: Exception) {
        }

        if (menu.isEmpty()) {
            if (isMain)
                menu.addAll(listOf(Section.CALENDAR, Section.HOME, Section.SEARCH, Section.CABINET))
            else
                menu.addAll(listOf(Section.BOOK, Section.MARKERS, Section.HOME, Section.SETTINGS))
        }

        return menu
    }

    fun saveMenu(isMain: Boolean, menu: List<Section>) {
        val name = if (isMain) MAIN_MENU else HOME_MENU
        val stream = DataOutputStream(
            BufferedOutputStream(context.openFileOutput(name, Context.MODE_PRIVATE))
        )
        menu.forEach {
            stream.writeInt(it.value)
            stream.flush()
        }
        stream.close()
    }

    fun loadItems(): List<HomeItem.Type> {
        val items = mutableListOf<HomeItem.Type>()
        try {
            val stream = DataInputStream(
                BufferedInputStream(context.openFileInput(HOME_ITEMS))
            )
            while (stream.available() > 0) {
                val item = when (stream.readInt()) {
                    HomeItem.Type.SUMMARY.value -> HomeItem.Type.SUMMARY
                    HomeItem.Type.NEWS.value -> HomeItem.Type.NEWS
                    HomeItem.Type.ADDITION.value -> HomeItem.Type.ADDITION
                    HomeItem.Type.CALENDAR.value -> HomeItem.Type.CALENDAR
                    HomeItem.Type.INFO.value -> HomeItem.Type.INFO
                    HomeItem.Type.JOURNAL.value -> HomeItem.Type.JOURNAL
                    else -> HomeItem.Type.MENU
                }
                items.add(item)
            }
            stream.close()
        } catch (ignore: Exception) {
        }

        if (items.isEmpty())
            items.addAll(
                listOf(
                    HomeItem.Type.CALENDAR, HomeItem.Type.ADDITION, HomeItem.Type.NEWS,
                    HomeItem.Type.MENU, HomeItem.Type.SUMMARY, HomeItem.Type.JOURNAL,
                    HomeItem.Type.INFO
                )
            )

        return items
    }

    fun saveItems(items: List<HomeItem.Type>) {
        val stream = DataOutputStream(
            BufferedOutputStream(
                context.openFileOutput(HOME_ITEMS, Context.MODE_PRIVATE)
            )
        )
        items.forEach {
            stream.writeInt(it.value)
            stream.flush()
        }
        stream.close()
    }

    fun getMenuList(selectSection: Section): List<MenuItem> {
        val list = mutableListOf<MenuItem>()
        for (i in listSection.indices) {
            val item = if (isMain && i == 0)
                MenuItem(R.drawable.ic_home, alterTitle)
            else MenuItem(listIcon[i], listTitle[i])
            if (selectSection == listSection[i]) item.isSelect = true
            list.add(item)
        }
        return list
    }

    fun getSectionByTitle(title: String): Section =
        if (title == alterTitle)
            listSection[0] else
            listSection[listTitle.indexOf(title)]
}