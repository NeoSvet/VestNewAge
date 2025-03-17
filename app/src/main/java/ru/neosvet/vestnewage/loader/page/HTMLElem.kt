package ru.neosvet.vestnewage.loader.page

import ru.neosvet.vestnewage.utils.fromHTML

class HTMLElem {
    var tag: String? = null
    var par = ""
    var start = false
    var end = false
    var html = ""
        set(value) {
            field = if (value.contains("&")) //"&#x"
                value.fromHTML
            else if (tag == Const.LINK)
                value.trim { it <= ' ' } else value
        }

    constructor()

    constructor(tag: String) {
        this.tag = tag
        start = tag != Const.PAR
        end = true
    }

    val code: String
        get() = when {
            tag == Const.LINE -> "<$tag>$html"
            !start && end -> "</$tag>"
            tag == Const.TEXT -> html
            tag == Const.IMAGE -> "<$tag $par"
            else -> {
                val s = if (par.isEmpty()) "<$tag>$html"
                else if (tag == Const.LINK) "<$tag href=\"$par\">$html"
                else "<$tag $par>$html"
                if (end) "$s</$tag>" else s
            }
        }
}