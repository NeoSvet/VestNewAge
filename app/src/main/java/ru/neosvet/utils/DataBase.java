package ru.neosvet.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.helpers.UnreadHelper;

public class DataBase extends SQLiteOpenHelper {
    public static final String PARAGRAPH = "par", SEARCH = "search",
            JOURNAL = "journal", MARKERS = "markers", LIKE = " LIKE ?",
            Q = " = ?",  COLLECTIONS = "collections", ID = "id",DESC = " DESC";
    private Context context;
    private String name = "";

    public DataBase(Context context, String name) {
        super(context, configName(name), null, 1);
        this.context = context;
        this.name = configName(name);
    }

    private static String configName(String name) {
        if (name.contains(Const.HTML))
            name = getDatePage(name);
        return name;
    }

    public String getName() {
        return name;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        if (name.contains(".")) { // базы данных с материалами
            db.execSQL("create table " + Const.TITLE + " ("
                    + ID + " integer primary key autoincrement," //id Const.TITLE
                    + Const.LINK + " text,"
                    + Const.TITLE + " text,"
                    + Const.TIME + " integer);");
            //записываем дату создания (в дальнейшем это будет дата изменений):
            ContentValues cv = new ContentValues();
            cv.put(Const.TIME, System.currentTimeMillis());
            db.insert(Const.TITLE, null, cv);
            db.execSQL("create table " + PARAGRAPH + " ("
                    + ID + " integer," //id Const.TITLE
                    + PARAGRAPH + " text);");
        } else if (name.equals(UnreadHelper.NAME)) {
            db.execSQL("create table if not exists " + UnreadHelper.NAME + " ("
                    + Const.LINK + " text primary key,"
                    + Const.TIME + " integer);");
        } else if (name.equals(SEARCH)) {
            db.execSQL("create table if not exists " + SEARCH + " ("
                    + Const.LINK + " text primary key,"
                    + Const.TITLE + " text,"
                    + ID + " integer," //number for sorting
                    + Const.DESCTRIPTION + " text);");
        } else if (name.equals(JOURNAL)) {
            db.execSQL("create table if not exists " + JOURNAL + " ("
                    + ID + " text primary key," // date&id Const.TITLE || date&id Const.TITLE&rnd_place
                    + Const.TIME + " integer);");
        } else if (name.equals(MARKERS)) {
            db.execSQL("create table " + MARKERS + " ("
                    + ID + " integer primary key autoincrement," //id закладки
                    + Const.LINK + " text," //ссылка на материал
                    + COLLECTIONS + " text," //список id подборок, в которые включен материал
                    + Const.DESCTRIPTION + " text,"  //описание
                    + Const.PLACE + " text);"); //место в материале
            db.execSQL("create table " + COLLECTIONS + " ("
                    + ID + " integer primary key autoincrement," //id подборок
                    + MARKERS + " text," //список id закладок
                    + Const.PLACE + " integer," //место подборки в списке подоборок
                    + Const.TITLE + " text);"); //название Подборки
            // добавляем подборку по умолчанию - "вне подборок":
            ContentValues cv = new ContentValues();
            cv.put(Const.TITLE, context.getResources().getString(R.string.no_collections));
            db.insert(COLLECTIONS, null, cv);
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
    public static String getContentPage(Context ctxt, String link, boolean onlyTitle) {
        DataBase dataBase = new DataBase(ctxt, link);
        SQLiteDatabase db = dataBase.getWritableDatabase();
        Cursor cursor = db.query(Const.TITLE, null,
                Const.LINK + DataBase.Q, new String[]{link},
                null, null, null);
        int id;

        StringBuilder pageCon = new StringBuilder();
        if (cursor.moveToFirst()) {
            pageCon.append(dataBase.getPageTitle(cursor.getString(cursor.getColumnIndex(Const.TITLE)), link));
            if (onlyTitle) {
                cursor.close();
                dataBase.close();
                return pageCon.toString();
            }
            pageCon.append(Const.N);
            pageCon.append(Const.N);
            id = cursor.getInt(cursor.getColumnIndex(DataBase.ID));
        } else { // страница не загружена...
            cursor.close();
            dataBase.close();
            return null;
        }
        cursor.close();
        cursor = db.query(DataBase.PARAGRAPH, new String[]{DataBase.PARAGRAPH},
                DataBase.ID + DataBase.Q, new String[]{String.valueOf(id)},
                null, null, null);
        if (cursor.moveToFirst()) {
            do {
                pageCon.append(Lib.withOutTags(cursor.getString(0)));
                pageCon.append(Const.N);
                pageCon.append(Const.N);
            } while (cursor.moveToNext());
        } else { // страница не загружена...
            cursor.close();
            dataBase.close();
            return null;
        }
        cursor.close();
        dataBase.close();
        pageCon.delete(pageCon.length() - 2, pageCon.length());
        return pageCon.toString();
    }

    public static String getDatePage(String link) {
        if (!link.contains("/") || link.contains("press"))
            return "00.00";
        else if (link.contains("pred")) {
            if (link.contains("2004"))
                return "12.04";
            else if (link.contains("2009"))
                return "01.09";
            else
                return "08.04";
        } else {
            if (link.contains("=")) { //http://blagayavest.info/poems/?date=11-3-2017
                link = link.substring(link.indexOf("-") + 1);
                if (link.length() == 6)
                    link = "0" + link;
                link= link.replace("-20", ".");
            } else if (link.contains("-")) {///2005/01-02.08.05.html
                link = link.substring(link.indexOf("-") + 4, link.lastIndexOf("."));
            } else { //http://blagayavest.info/poems/11.03.17.html
                link = link.substring(link.lastIndexOf("/") + 4, link.lastIndexOf("."));
                if (link.contains("_")) link = link.substring(0, link.indexOf("_"));
                if (link.contains("#")) link = link.substring(0, link.indexOf("#"));
            }
            return link;
        }
    }

    public String getPageTitle(String title, String link) {
        if (name.equals("00.00") || link.contains("2004") || link.contains("pred")) {
            return title;
        } else {
            String s = link.substring(link.lastIndexOf("/") + 1, link.lastIndexOf("."));
            if (s.contains("_")) s = s.substring(0, s.indexOf("_"));
            if (s.contains("#")) s = s.substring(0, s.indexOf("#"));
            if (link.contains(Const.POEMS)) {
                s += " " + context.getResources().getString(R.string.katren)
                        + " " + Const.KV_OPEN + title + Const.KV_CLOSE;
            } else
                s += " " + title;
            return s;
        }
    }

    public int getPageId(String link) {
        DataBase dataBase = new DataBase(context, name);
        SQLiteDatabase db = dataBase.getWritableDatabase();
        Cursor cursor = db.query(Const.TITLE,
                new String[]{DataBase.ID},
                Const.LINK + DataBase.Q, new String[]{link},
                null, null, null);
        int r = -1;
        if (cursor.moveToFirst())
            r = cursor.getInt(0);
        cursor.close();
        dataBase.close();
        return r;
    }

    public boolean existsPage(String link) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor curTitle = db.query(Const.TITLE, new String[]{DataBase.ID},
                Const.LINK + DataBase.Q, new String[]{link}, null, null, null);
        boolean exists = false;
        if (curTitle.moveToFirst()) {
            Cursor curPar = db.query(DataBase.PARAGRAPH, null,
                    DataBase.ID + DataBase.Q,
                    new String[]{String.valueOf(curTitle.getInt(0))}
                    , null, null, null);
            exists = curPar.moveToFirst();
            curPar.close();
        }
        curTitle.close();
        return exists;
    }
}
