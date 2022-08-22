package ru.neosvet.vestnewage.utils

val String.isPoem: Boolean
    get() = this.contains(Const.POEMS)

val String.date: String
    get() {
        val s = this.substring(this.lastIndexOf("/") + 1)
        val i = s.indexOf("_")
        return if (i > -1)  s.substring(0, i)
        else s.substring(0, s.lastIndexOf("."))
    }

val String.noHasDate: Boolean
    get() = this.contains("2004") || this.contains("pred")