package ru.neosvet.vestnewage.view.basic

import com.google.android.material.tabs.TabLayout

fun TabLayout.select(index: Int) {
    val tab = getTabAt(index)
    selectTab(tab)
}