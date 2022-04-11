package ru.neosvet.vestnewage.helpers;

import android.database.Cursor;

import java.io.File;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.model.SiteModel;
import ru.neosvet.vestnewage.storage.PageStorage;

public class ListsHelper {
    private final long timeNow;
    private byte bookIsOld = 0;

    public ListsHelper() {
        timeNow = DateHelper.initNow().getTimeInSeconds();
    }

    public boolean summaryIsOld() {
        File file = Lib.getFile(Const.RSS);
        long time = file.lastModified() / DateHelper.SEC_IN_MILLS;
        return timeNow - time > DateHelper.DAY_IN_SEC;
    }

    public boolean siteIsOld() {
        long time = Lib.getFile(SiteModel.MAIN).lastModified() / DateHelper.SEC_IN_MILLS;
        if (timeNow - time > DateHelper.DAY_IN_SEC)
            return true;
        time = Lib.getFile(SiteModel.NEWS).lastModified() / DateHelper.SEC_IN_MILLS;
        return timeNow - time > DateHelper.DAY_IN_SEC;
    }

    public boolean bookIsOld() {
        if (bookIsOld == 0) {
            DateHelper d = DateHelper.initNow();
            d.changeMonth(-1);
            PageStorage storage = new PageStorage();
            storage.open(d.getMY());
            Cursor curTime = storage.getTime();
            if (!curTime.moveToFirst() || curTime.getCount() < 2) {
                bookIsOld = 1;
            } else {
                long time = curTime.getLong(0) / DateHelper.SEC_IN_MILLS;
                bookIsOld = timeNow - time > DateHelper.DAY_IN_SEC ? 1 : (byte) 2;
                if (bookIsOld == 2) {
                    curTime.close();
                    storage.close();
                    d = DateHelper.putYearMonth(2016, 1);
                    storage.open(d.getMY());
                    curTime = storage.getTime();
                    if (!curTime.moveToFirst() || curTime.getCount() < 2) {
                        bookIsOld = 1;
                    } else {
                        time = curTime.getLong(0) / DateHelper.SEC_IN_MILLS;
                        bookIsOld = timeNow - time > DateHelper.DAY_IN_SEC ? 1 : (byte) 2;
                    }
                }
            }
            curTime.close();
            storage.close();
        }
        return bookIsOld == 1;
    }

}
