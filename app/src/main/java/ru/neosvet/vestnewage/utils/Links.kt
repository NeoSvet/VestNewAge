package ru.neosvet.vestnewage.utils

import java.util.regex.Pattern

private val patternDate = Pattern.compile("\\d{2}\\.\\d{2}.\\d{2}")

val String.isPoem: Boolean
    get() = this.contains(Const.POEMS)

val String.date: String
    get() {
        val m = patternDate.matcher(this)
        m.find()
        val i = m.start()
        return this.substring(i, i + 8)
    }

val String.noHasDate: Boolean
    get() = !patternDate.matcher(this).find()