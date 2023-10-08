package ru.neosvet.vestnewage.utils

import ru.neosvet.vestnewage.App
import java.io.File

object Files {
    fun getFile(name: String): File {
        return File(App.context.filesDir.toString() + name)
    }

    fun getFileS(name: String): File {
        return File(App.context.filesDir.toString() + File.separator + name)
    }

    fun getFileP(name: String): File {
        return File(App.context.filesDir.parent!! + name)
    }

    fun getFileDB(name: String): File {
        return getFileP("/databases/$name")
    }

    fun getFileL(link: String): File {
        var file = getFile(link.substring(0, link.lastIndexOf("/")))
        if (!file.exists()) file.mkdirs()
        file = getFile(link)
        return file
    }
}