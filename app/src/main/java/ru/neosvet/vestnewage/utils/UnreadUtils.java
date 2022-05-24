package ru.neosvet.vestnewage.utils;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;

import java.util.ArrayList;
import java.util.List;

import me.leolin.shortcutbadger.ShortcutBadger;
import ru.neosvet.vestnewage.App;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.data.DataBase;
import ru.neosvet.vestnewage.data.DateUnit;
import ru.neosvet.vestnewage.storage.PageStorage;

/**
 * Created by NeoSvet on 03.02.2018.
 */

public class UnreadUtils {
    public static final String NAME = "noread";
    private DataBase dbUnread;
    private PageStorage storage = new PageStorage();
    private long time = 0;
    private int[] ids_new;
    private boolean isClosed = true;

    private void open() {
        if (!isClosed) return;
        dbUnread = new DataBase(NAME);
        isClosed = false;
    }

    public boolean addLink(String link, DateUnit date) {
        open();
        if (!link.contains(Const.HTML)) link += Const.HTML;
        storage.open(link);
        if (storage.existsPage(link)) return false; // скаченную страницу игнорируем
        link = link.replace(Const.HTML, "");
        Cursor cursor = dbUnread.query(NAME, new String[]{Const.LINK}, Const.LINK + DataBase.Q, link);
        boolean exists = cursor.moveToFirst();
        cursor.close();
        if (exists) return true; // уже есть в списке непрочитанного
        ContentValues row = new ContentValues();
        row.put(Const.TIME, date.getTimeInMills());
        row.put(Const.LINK, link);
        dbUnread.insert(NAME, row);
        time = System.currentTimeMillis();
        return true;
    }

    public void deleteLink(String link) {
        open();
        link = link.replace(Const.HTML, "");
        if (dbUnread.delete(NAME, Const.LINK + DataBase.Q, link) > 0) {
            time = System.currentTimeMillis();
            setBadge();
        }
        close();
    }

    public List<String> getList() {
        open();
        List<String> links = new ArrayList<>();
        Cursor cursor = dbUnread.query(NAME, null, null,
                null, null, null, Const.TIME);
        if (cursor.moveToFirst()) {
            int iLink = cursor.getColumnIndex(Const.LINK);
            int iTime = cursor.getColumnIndex(Const.TIME);
            do {
                if (cursor.getLong(iTime) > 0)
                    links.add(cursor.getString(iLink));
            } while (cursor.moveToNext());
        }
        cursor.close();
        close();
        return links;
    }

    public int getCount() {
        open();
        Cursor cursor = dbUnread.query(NAME, null, null,
                null, null, null, Const.TIME);
        int k = 0;
        if (cursor.moveToFirst())
            k = cursor.getCount();
        cursor.close();
        AdsUtils ads = new AdsUtils(App.context);
        k += ads.getUnreadCount();
        ads.close();
        close();
        return k;
    }

    public int getNewId(int k) {
        if (ids_new == null)
            ids_new = new int[]{R.drawable.ic_0, R.drawable.ic_1, R.drawable.ic_2,
                    R.drawable.ic_3, R.drawable.ic_4, R.drawable.ic_5, R.drawable.ic_6,
                    R.drawable.ic_7, R.drawable.ic_8, R.drawable.ic_9};
        if (k < ids_new.length)
            return ids_new[k];
        else
            return R.drawable.ic_more;
    }

    public long lastModified() {
        SharedPreferences pref = App.context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
        return pref.getLong(Const.TIME, 0);
    }

    public void clearList() {
        open();
        dbUnread.delete(NAME);
        time = System.currentTimeMillis();
    }

    private void close() {
        if (isClosed) return;
        storage.close();
        dbUnread.close();
        isClosed = true;
        if (time > 0) {
            SharedPreferences pref = App.context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = pref.edit();
            editor.putLong(Const.TIME, time);
            editor.apply();
        }
    }

    public void setBadge() {
        AdsUtils ads = new AdsUtils(App.context);
        setBadge(ads.getUnreadCount());
        ads.close();
    }

    public void setBadge(int count_ads) {
        open();
        Cursor cursor = dbUnread.query(NAME, null, Const.TIME + " > ?", "0");
        count_ads += cursor.getCount();
        if (count_ads == 0)
            ShortcutBadger.removeCount(App.context);
        else
            ShortcutBadger.applyCount(App.context, count_ads);
        cursor.close();
        close();
    }
}
