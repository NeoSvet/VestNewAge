package ru.neosvet.vestnewage.utils;

import android.database.Cursor;

import java.io.File;

import ru.neosvet.vestnewage.data.DateUnit;
import ru.neosvet.vestnewage.model.SiteModel;
import ru.neosvet.vestnewage.storage.PageStorage;

public class ListsUtils {
    private final long timeNow;
    private byte bookIsOld = 0;

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
        File file = Lib.getFile(SiteModel.MAIN);
        if (!file.exists())
            return true;
        long time = file.lastModified() / DateUnit.SEC_IN_MILLS;
        if (timeNow - time > DateUnit.DAY_IN_SEC)
            return true;
        file = Lib.getFile(SiteModel.NEWS);
        if (!file.exists())
            return true;
        time = file.lastModified() / DateUnit.SEC_IN_MILLS;
        return timeNow - time > DateUnit.DAY_IN_SEC;
    }

    public boolean bookIsOld() {
        if (bookIsOld == 0) {
            DateUnit d = DateUnit.initNow();
            d.changeMonth(-1);
            PageStorage storage = new PageStorage();
            storage.open(d.getMY());
            Cursor curTime = storage.getTime();
            if (!curTime.moveToFirst() || curTime.getCount() < 2) {
                bookIsOld = 1;
            } else {
                long time = curTime.getLong(0) / DateUnit.SEC_IN_MILLS;
                bookIsOld = timeNow - time > DateUnit.DAY_IN_SEC ? 1 : (byte) 2;
                if (bookIsOld == 2) {
                    curTime.close();
                    storage.close();
                    d = DateUnit.putYearMonth(2016, 1);
                    storage.open(d.getMY());
                    curTime = storage.getTime();
                    if (!curTime.moveToFirst() || curTime.getCount() < 2) {
                        bookIsOld = 1;
                    } else {
                        time = curTime.getLong(0) / DateUnit.SEC_IN_MILLS;
                        bookIsOld = timeNow - time > DateUnit.DAY_IN_SEC ? 1 : (byte) 2;
                    }
                }
            }
            curTime.close();
            storage.close();
        }
        return bookIsOld == 1;
    }

}
