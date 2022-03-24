package ru.neosvet.vestnewage.loader

import android.content.Context

interface ListLoader {
    fun getLinkList(context: Context): Int
}