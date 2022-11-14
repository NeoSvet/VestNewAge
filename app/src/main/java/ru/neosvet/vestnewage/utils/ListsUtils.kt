package ru.neosvet.vestnewage.utils;

import java.io.File;

import ru.neosvet.vestnewage.data.DateUnit;
import ru.neosvet.vestnewage.viewmodel.SiteToiler;

public class ListsUtils {
    private final long timeNow;

    public ListsUtils() {
        timeNow = DateUnit.initNow().getTimeInSeconds();
    }

    public boolean summaryIsOld() {
        File file = Lib.getFile(Const.RSS);
        if (!file.exists())
            return true;
        long time = file.lastModified() / DateUnit.SEC_IN_MILLS;
        return timeNow - time > DateUnit.DAY_IN_SEC;
    }

    public boolean siteIsOld() {
        File file = Lib.getFile(SiteToiler.MAIN);
        if (!file.exists())
            return true;
        long time = file.lastModified() / DateUnit.SEC_IN_MILLS;
        if (timeNow - time > DateUnit.DAY_IN_SEC)
            return true;
        file = Lib.getFile(SiteToiler.NEWS);
        if (!file.exists())
            return true;
        time = file.lastModified() / DateUnit.SEC_IN_MILLS;
        return timeNow - time > DateUnit.DAY_IN_SEC;
    }

}
