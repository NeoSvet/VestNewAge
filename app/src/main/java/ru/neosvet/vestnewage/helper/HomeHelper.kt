package ru.neosvet.vestnewage.helper

import android.content.Context
import ru.neosvet.vestnewage.data.HomeItem
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
                    else -> Section.MENU
                }
                menu.add(item)
            }
            stream.close()
        } catch (e: Exception) {
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
        } catch (e: Exception) {
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
}