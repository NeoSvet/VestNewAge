package ru.neosvet.vestnewage.helpers;

import android.content.Context;
import android.database.Cursor;

import java.io.File;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.fragment.SiteFragment;

public class ListsHelper {
    private final Context context;
    private final long timeNow;
    private byte bookIsOld = 0;

    public ListsHelper(Context context) {
        this.context = context;
        timeNow = DateHelper.initNow(context).getTimeInSeconds();
    }

    public boolean summaryIsOld() {
        File file = new File(context.getFilesDir() + Const.RSS);
        long time = file.lastModified() / DateHelper.SEC_IN_MILLS;
        return timeNow - time > DateHelper.DAY_IN_SEC;
    }

    public boolean siteIsOld() {
        Lib lib = new Lib(context);
        long time = lib.getFileByName(SiteFragment.MAIN).lastModified() / DateHelper.SEC_IN_MILLS;
        if (timeNow - time > DateHelper.DAY_IN_SEC)
            return true;
        time = lib.getFileByName(SiteFragment.NEWS).lastModified() / DateHelper.SEC_IN_MILLS;
        return timeNow - time > DateHelper.DAY_IN_SEC;
    }

    public boolean bookIsOld() {
        if (bookIsOld == 0) {
            DateHelper d = DateHelper.initNow(context);
            d.changeMonth(-1);
            DataBase dataBase = new DataBase(context, d.getMY());
            Cursor curTime = dataBase.query(Const.TITLE, new String[]{Const.TIME});
            if (!curTime.moveToFirst() || curTime.getCount() < 2) {
                bookIsOld = 1;
            } else {
                long time = curTime.getLong(0) / DateHelper.SEC_IN_MILLS;
                bookIsOld = timeNow - time > DateHelper.DAY_IN_SEC ? 1 : (byte) 2;
                if (bookIsOld == 2) {
                    curTime.close();
                    dataBase.close();
                    d = DateHelper.putYearMonth(context, 2016, 1);
                    dataBase = new DataBase(context, d.getMY());
                    curTime = dataBase.query(Const.TITLE, new String[]{Const.TIME});
                    if (!curTime.moveToFirst() || curTime.getCount() < 2) {
                        bookIsOld = 1;
                    } else {
                        time = curTime.getLong(0) / DateHelper.SEC_IN_MILLS;
                        bookIsOld = timeNow - time > DateHelper.DAY_IN_SEC ? 1 : (byte) 2;
                    }
                }
            }
            curTime.close();
            dataBase.close();
        }
        return bookIsOld == 1;
    }

}
