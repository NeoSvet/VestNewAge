package ru.neosvet.vestnewage.utils

val String.isPoem: Boolean
    get() = this.contains(Const.POEMS)