package ru.neosvet.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import ru.neosvet.vestnewage.R;

public class DataBase extends SQLiteOpenHelper {
    public static final String JOURNAL = "journal", MARKERS = "markers",// LIST = "list",
            Q = " = ?", TITLE = "title", COLLECTIONS = "collections", ID = "id",
            LINK = "link", TIME = "time", PLACE = "place", DESCTRIPTION = "des", DESC = " DESC";
    private Context context;
    private String name;

    public DataBase(Context context, String name) {
        super(context, name, null, 1);
        this.context = context;
        this.name = name;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        if (name.equals(JOURNAL)) {
            db.execSQL("create table if not exists " + JOURNAL + " ("
                    + LINK + " text primary key,"
                    + TITLE + " text,"
                    + TIME + " integer);");
        } else {
            db.execSQL("create table if not exists " + MARKERS + " ("
                    + ID + " integer primary key autoincrement," //id закладки
                    + LINK + " text," //ссылка на материал
                    + COLLECTIONS + " text," //список id подборок, в которые включен материал
                    + DESCTRIPTION + " text,"  //описание
                    + PLACE + " text);"); //место в материале
            db.execSQL("create table if not exists " + COLLECTIONS + " ("
                    + ID + " integer primary key autoincrement," //id подборок
                    + MARKERS + " text," //список id закладок
                    + PLACE + " integer," //место подборки в списке подоборок
                    + TITLE + " text);"); //название Подборки
            Cursor cursor = db.query(COLLECTIONS, null, null, null, null, null, null);
            if (cursor.getCount() == 0) {
                ContentValues cv = new ContentValues();
                cv.put(TITLE, context.getResources().getString(R.string.no_collections));
                db.insert(COLLECTIONS, null, cv);
            }
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    // для закладок и подборок:
    public static String closeList(String s) {
        if (s == null) return "";
        return "," + s + ",";
    }

    public static String openList(String s) {
        if (s.length() > 0) {
            s = s.trim();
            if (s.substring(s.length() - 1).equals(","))
                s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    public static String[] getList(String s) {
        if (s.contains(","))
            return s.split(",");
        else
            return new String[]{s};
    }
}
