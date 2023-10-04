package ru.neosvet.vestnewage.utils

import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.storage.AdsStorage
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
        val file = Lib.getFile(SiteToiler.MAIN)
        if (!file.exists()) return true
        var time = file.lastModified() / DateUnit.SEC_IN_MILLS
        if (timeNow - time > DateUnit.DAY_IN_SEC) return true
        val storage = AdsStorage().site
        time = storage.getTime() / DateUnit.SEC_IN_MILLS
        storage.close()
        return timeNow - time > DateUnit.DAY_IN_SEC
    }
}