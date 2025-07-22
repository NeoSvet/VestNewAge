package ru.neosvet.vestnewage.utils

import androidx.core.text.HtmlCompat
import ru.neosvet.vestnewage.data.DateUnit
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

val String.dateFromLink: DateUnit
    get() = if (contains("predislovie")) when {
        contains("2009") -> DateUnit.putYearMonth(2009, 1).apply { day = 1 }
        contains("2004") -> DateUnit.putYearMonth(2004, 12).apply { day = 31 }
        else -> DateUnit.putYearMonth(2004, 8).apply { day = 26 }
    } else DateUnit.parse(date)

val String.hasDate: Boolean
    get() = patternDate.matcher(this).find()

val String.fromHTML: String
    get() = HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()

val String.isDoctrineBook: Boolean
    get() = this.indexOf('-') < 13 && this[9].isDigit()