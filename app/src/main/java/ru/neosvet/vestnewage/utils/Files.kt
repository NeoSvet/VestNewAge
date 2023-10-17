package ru.neosvet.vestnewage.utils

import ru.neosvet.vestnewage.App
import java.io.File

object Files {
    const val RSS = "/rss"
    const val DATE = "date"

    fun file(name: String): File {
        return File(App.context.filesDir.toString() + name)
    }

    fun slash(name: String): File {
        return File(App.context.filesDir.toString() + File.separator + name)
    }

    fun parent(name: String): File {
        return File(App.context.filesDir.parent!! + name)
    }

    fun dateBase(name: String): File {
        return parent("/databases/$name")
    }

    fun link(link: String): File {
        var file = file(link.substring(0, link.lastIndexOf("/")))
        if (!file.exists()) file.mkdirs()
        file = file(link)
        return file
    }
}