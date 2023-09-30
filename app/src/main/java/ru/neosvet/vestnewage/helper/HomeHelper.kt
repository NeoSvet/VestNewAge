package ru.neosvet.vestnewage.helper

import android.content.Context
import ru.neosvet.vestnewage.data.HomeItem
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class HomeHelper(private val context: Context) {
    companion object {
        private const val HOME_ITEMS = "homeitems.dat"
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