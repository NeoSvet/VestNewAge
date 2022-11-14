package ru.neosvet.vestnewage.utils

import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.viewmodel.SiteToiler

class ListsUtils {
    private val timeNow = DateUnit.initNow().timeInSeconds

    fun summaryIsOld(): Boolean {
        val file = Lib.getFile(Const.RSS)
        if (!file.exists()) return true
        val time = file.lastModified() / DateUnit.SEC_IN_MILLS
        return timeNow - time > DateUnit.DAY_IN_SEC
    }

    fun siteIsOld(): Boolean {
        var file = Lib.getFile(SiteToiler.MAIN)
        if (!file.exists()) return true
        var time = file.lastModified() / DateUnit.SEC_IN_MILLS
        if (timeNow - time > DateUnit.DAY_IN_SEC) return true
        file = Lib.getFile(SiteToiler.NEWS)
        if (!file.exists()) return true
        time = file.lastModified() / DateUnit.SEC_IN_MILLS
        return timeNow - time > DateUnit.DAY_IN_SEC
    }
}