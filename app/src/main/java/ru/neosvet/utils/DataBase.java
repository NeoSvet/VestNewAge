package ru.neosvet.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import ru.neosvet.vestnewage.R;

public class DataBase extends SQLiteOpenHelper {
    public static final String PARAGRAPH = "paragraph",
            JOURNAL = "journal", MARKERS = "markers",// LIST = "list",
            Q = " = ?", TITLE = "title", COLLECTIONS = "collections", ID = "id",
            LINK = "link", TIME = "time", PLACE = "place", DESCTRIPTION = "des", DESC = " DESC";
    private Context context;
    private String name;

    public DataBase(Context context, String name) {
        super(context, name, null, 1);
        this.context = context;
        if (name.contains(".html"))
            this.name = getDatePage(name);
        else
            this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        if (name.contains(".")) { // базы данных с материалами
            db.execSQL("create table if not exists " + TITLE + " ("
                    + ID + " integer primary key autoincrement," //id TITLE
                    + LINK + " text,"
                    + TITLE + " text,"
                    + TIME + " integer);");
            Cursor cursor = db.query(TITLE, null, null, null, null, null, null);
            if (cursor.getCount() == 0) { //если таблица пуста, то записываем дату создания
                // в дальнейшем это будет дата изменений
                ContentValues cv = new ContentValues();
                cv.put(TIME, System.currentTimeMillis());
                db.insert(TITLE, null, cv);
            }
            cursor.close();
            db.execSQL("create table if not exists " + PARAGRAPH + " ("
                    + ID + " integer," //id TITLE
                    + PARAGRAPH + " text);");
        } else if (name.equals(JOURNAL)) {
            db.execSQL("create table if not exists " + JOURNAL + " ("
                    + ID + " text primary key," // date&id
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
            cursor.close();
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

    // для материалов в базах данных:
    public String getDatePage(String link) {
        if (!link.contains("/") || link.contains("press"))
            return "00.00";
        else {
            if (link.contains("-")) { //http://blagayavest.info/poems/?date=11-3-2017
                link = link.substring(link.indexOf("-") + 1);
                if (link.length() == 6)
                    link = "0" + link;
                return link.replace("-20", ".");
            } else { //http://blagayavest.info/poems/11.03.17.html
                link = link.substring(link.lastIndexOf(".") - 5, link.lastIndexOf("."));
                if (link.contains("_")) link = link.substring(0, link.indexOf("_"));
                return link;
            }
        }
    }

    public String getPageTitle(String title, String link) {
        if (name.equals("00.00")) {
            return title;
        } else {
            String s = link.substring(link.lastIndexOf("/") + 1, link.lastIndexOf("."));
            if (s.contains("_"))
                s = s.substring(0, s.indexOf("_"));
            if (link.contains(Lib.POEMS)) {
                s += " " + context.getResources().getString(R.string.katren)
                        + " " + Lib.KV_OPEN + title + "”";
            } else
                s += " " + title;
            return s;
        }
    }

    public int getPageId(String link) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.query(DataBase.TITLE,
                new String[]{DataBase.ID},
                DataBase.LINK + DataBase.Q, new String[]{link},
                null, null, null);
        int r = -1;
        if (cursor.moveToFirst())
            r = cursor.getInt(0);
        cursor.close();
        this.close();
        return r;
    }

    public boolean existsPage(String link) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(p.id) FROM paragraph p INNER JOIN title t ON t.id = p.id WHERE t.link"
                + DataBase.Q, new String[]{link});
        cursor.moveToFirst();
        boolean b = cursor.getInt(0) > 0; //count > 0
        cursor.close();
        this.close();
        return b;
    }
}
