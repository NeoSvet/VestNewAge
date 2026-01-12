package ru.neosvet.vestnewage.data

import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.utils.Const

class CalendarItem(val num: Int, idColor: Int) {
    var color = ContextCompat.getColor(App.context, idColor)
    private val titles = mutableListOf<String>()
    private val links = mutableListOf<String>()
    var isBold = num < 1
    private var poem = false
    private var epistle = false
    val Titles: List<String>
        get() = titles
    val Links: List<String>
        get() = links

    fun clear() {
        if (titles.isNotEmpty()) {
            titles.clear()
            links.clear()
            poem = false
            epistle = false
        }
    }

    val count: Int
        get() = links.size

    fun addTitle(title: String) {
        if (title.contains("№"))
            titles.add(title.substring(title.indexOf("№")))
        else titles.add(title)
    }

    fun addLink(link: String) {
        if (link.contains(Const.POEMS)) poem = true else epistle = true
        links.add(link)
    }

    val background: Drawable?
        get() {
            val bg = when {
                poem && epistle -> R.drawable.cell_bg_all
                poem -> R.drawable.cell_bg_poe
                epistle -> R.drawable.cell_bg_epi
                else -> R.drawable.cell_bg_none
            }
            return ContextCompat.getDrawable(App.context, bg)
        }
    val text: String
        get() = if (num < 1)
            App.context.resources.getStringArray(R.array.week_day)[num * -1]
        else num.toString()
}