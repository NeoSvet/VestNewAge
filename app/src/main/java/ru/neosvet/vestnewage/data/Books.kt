package ru.neosvet.vestnewage.data

import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.DataBase
import ru.neosvet.vestnewage.utils.Const

object Books {
    fun linkToBook(link: String) = when {
        link.contains(Const.HOLY_RUS) -> BookTab.HOLY_RUS
        link.contains(Const.WORLD_AFTER_WAR) -> BookTab.WORLD_AFTER_WAR
        else -> BookTab.DOCTRINE
    }

    fun baseUrl(book: BookTab) = when (book) {
        BookTab.HOLY_RUS -> Urls.HolyRusBase
        BookTab.WORLD_AFTER_WAR -> Urls.WorldAfterWarBase
        else -> Urls.DoctrineBase
    }

    fun siteUrl(book: BookTab) = when (book) {
        BookTab.HOLY_RUS -> Urls.HolyRusSite
        BookTab.WORLD_AFTER_WAR -> Urls.WorldAfterWarSite
        else -> Urls.DoctrineSite
    }

    fun baseName(book: BookTab) = when (book) {
        BookTab.HOLY_RUS -> DataBase.HOLY_RUS
        BookTab.WORLD_AFTER_WAR -> DataBase.WORLD_AFTER_WAR
        else -> DataBase.DOCTRINE
    }

    fun Prefix(book: BookTab) = when (book) {
        BookTab.HOLY_RUS -> Const.HOLY_RUS
        BookTab.WORLD_AFTER_WAR -> Const.WORLD_AFTER_WAR
        else -> Const.DOCTRINE
    }
}