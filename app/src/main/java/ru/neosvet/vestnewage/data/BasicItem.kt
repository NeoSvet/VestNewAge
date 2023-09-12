package ru.neosvet.vestnewage.data

import android.util.Pair

class BasicItem {
    var title: String
    var des: String = ""
    private val heads = NeoList<String>()
    private val listLinks = NeoList<String>()
    private var few = false

    constructor(title: String, link: String) {
        this.title = title
        addLink(link)
    }

    constructor(title: String) {
        this.title = title
    }

    constructor(title: String, onlyTitle: Boolean) {
        this.title = title
        if (onlyTitle) addLink("#")
    }

    val link: String
        get() = if (listLinks.isNotEmpty) listLinks.first() else ""

    val head: String
        get() = if (heads.isNotEmpty) heads.first() else ""

    val links: Iterator<String>
        get() {
            listLinks.reset(true)
            return listLinks
        }

    fun hasLink(): Boolean {
        return listLinks.isNotEmpty
    }

    fun hasFewLinks(): Boolean {
        return few
    }

    fun clear() {
        heads.clear()
        listLinks.clear()
        few = false
    }

    fun addLink(head: String, link: String) {
        heads.add(head)
        addLink(link)
    }

    fun addLink(link: String) {
        if (!few) few = listLinks.isNotEmpty
        listLinks.add(link)
    }

    fun addHead(head: String) {
        heads.add(head)
    }

    fun headsAndLinks(): Iterator<Pair<String, String>> {
        heads.reset(true)
        listLinks.reset(true)
        return object : Iterator<Pair<String, String>> {
            override fun hasNext(): Boolean {
                return heads.hasNext() && listLinks.hasNext()
            }

            override fun next(): Pair<String, String> {
                return Pair(heads.next(), listLinks.next())
            }
        }
    }

    override fun toString(): String {
        return if (few)
            this.javaClass.simpleName + "[title=$title, links=$listLinks, des=$des, heads=$heads]"
        else
            this.javaClass.simpleName + "[title=$title, link=$link, des=$des, head=$head]"
    }
}