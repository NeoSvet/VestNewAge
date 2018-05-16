package ru.neosvet.utils;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import me.leolin.shortcutbadger.ShortcutBadger;

/**
 * Created by NeoSvet on 03.02.2018.
 */

public class Noread {
    public static final String NAME = "noread";
    private Context context;
    private DataBase dbNoread, dbPages;
    private SQLiteDatabase db;
    private long time = 0;

    public Noread(Context context) {
        this.context = context;
        dbNoread = new DataBase(context, NAME);
        db = dbNoread.getWritableDatabase();
    }

    public boolean addLink(String link, Date date) {
        if (!link.contains(Const.HTML)) link += Const.HTML;
        if (dbPages == null) {
            dbPages = new DataBase(context, link);
        } else if (!dbPages.getName().equals(DataBase.getDatePage(link))) {
            dbPages.close();
            dbPages = new DataBase(context, link);
        }
        if (dbPages.existsPage(link)) return false; // скаченную страницу игнорируем
        link = link.replace(Const.HTML, "");
        Cursor cursor = db.query(NAME, new String[]{DataBase.LINK},
                DataBase.LINK + DataBase.Q, new String[]{link}, null, null, null);
        boolean exists = cursor.moveToFirst();
        cursor.close();
        if (exists) return true; // уже есть в списке непрочитанного
        ContentValues cv = new ContentValues();
        cv.put(DataBase.TIME, date.getTime());
        cv.put(DataBase.LINK, link);
        db.insert(NAME, null, cv);
        time = System.currentTimeMillis();
        return true;
    }

    public void deleteLink(String link) {
        link = link.replace(Const.HTML, "");
        if (db.delete(NAME, DataBase.LINK
                + DataBase.Q, new String[]{link}) > 0) {
            time = System.currentTimeMillis();
            setBadge();
        }
        close();
    }

    public List<String> getList() {
        List<String> links = new ArrayList<String>();
        Cursor cursor = db.query(NAME, null, null,
                null, null, null, DataBase.TIME);
        if (cursor.moveToFirst()) {
            int iLink = cursor.getColumnIndex(DataBase.LINK);
            int iTime = cursor.getColumnIndex(DataBase.TIME);
            do {
                if (cursor.getLong(iTime) > 0)
                    links.add(cursor.getString(iLink));
            } while (cursor.moveToNext());
        }
        db.close();
        dbNoread.close();
        return links;
    }

    public long lastModified() {
        SharedPreferences pref = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
        return pref.getLong(DataBase.TIME, 0);
    }

    public void clearList() {
        Cursor cursor = db.query(NAME, null, null,
                null, null, null, null);
        if (cursor.moveToFirst()) {
            int iLink = cursor.getColumnIndex(DataBase.LINK);
            int iTime = cursor.getColumnIndex(DataBase.TIME);
            ContentValues cv;
            do {
                if (cursor.getLong(iTime) > 0) {
                    cv = new ContentValues();
                    cv.put(DataBase.TIME, 0);
                    db.update(NAME, cv, DataBase.LINK + DataBase.Q,
                            new String[]{cursor.getString(iLink)});
                }
            } while (cursor.moveToNext());
            time = System.currentTimeMillis();
        }
    }

    public void close() {
        if (dbPages != null)
            dbPages.close();
        db.close();
        dbNoread.close();
        if (time > 0) {
            SharedPreferences pref = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = pref.edit();
            editor.putLong(DataBase.TIME, time);
            editor.apply();
        }
    }

    public void setBadge() {
        Cursor cursor = db.query(NAME, null, DataBase.TIME + " > ?",
                new String[]{"0"}, null, null, null);
        if (cursor.getCount() == 0)
            ShortcutBadger.removeCount(context);
        else
            ShortcutBadger.applyCount(context, cursor.getCount());
        cursor.close();
    }
}
