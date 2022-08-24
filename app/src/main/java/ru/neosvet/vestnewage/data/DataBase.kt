package ru.neosvet.vestnewage.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import ru.neosvet.vestnewage.App;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.storage.AdsStorage;
import ru.neosvet.vestnewage.utils.Const;
import ru.neosvet.vestnewage.utils.UnreadUtils;

public class DataBase extends SQLiteOpenHelper {
    public static final String PARAGRAPH = "par", JOURNAL = "journal",
            MARKERS = "markers", LIKE = " LIKE ?", GLOB = " GLOB ?",
            Q = " = ?", AND = " AND ", ID = "id", DESC = " DESC",
            COLLECTIONS = "collections",
            ARTICLES = "00.00", DOCTRINE = "00.01";
    public static final long EMPTY_BASE_SIZE = 24576L;
    private final SQLiteDatabase db;

    public DataBase(String name) {
        super(App.context, name, null, 1);
        db = this.getWritableDatabase();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        if (getDatabaseName().contains(".")) { // базы данных с материалами
            db.execSQL("create table " + Const.TITLE + " ("
                    + ID + " integer primary key autoincrement," //id Const.TITLE
                    + Const.LINK + " text,"
                    + Const.TITLE + " text,"
                    + Const.TIME + " integer);");
            //записываем дату создания (в дальнейшем это будет дата изменений):
            ContentValues row = new ContentValues();
            row.put(Const.TIME, System.currentTimeMillis());
            db.insert(Const.TITLE, null, row);
            db.execSQL("create table " + PARAGRAPH + " ("
                    + ID + " integer," //id Const.TITLE
                    + PARAGRAPH + " text);");
            return;
        }
        switch (getDatabaseName()) {
            case UnreadUtils.NAME:
                db.execSQL("create table if not exists " + UnreadUtils.NAME + " ("
                        + Const.LINK + " text primary key,"
                        + Const.TIME + " integer);");
                break;
            case AdsStorage.NAME:
                db.execSQL("create table if not exists " + AdsStorage.NAME + " ("
                        + Const.MODE + " integer,"
                        + Const.UNREAD + " integer default 1,"
                        + Const.TITLE + " text,"
                        + Const.LINK + " text,"
                        + Const.DESCTRIPTION + " text);");
                break;
            case Const.SEARCH:
                db.execSQL("create table if not exists " + Const.SEARCH + " ("
                        + Const.LINK + " text primary key,"
                        + Const.TITLE + " text,"
                        + ID + " integer," //number for sorting
                        + Const.DESCTRIPTION + " text);");
                break;
            case JOURNAL:
                db.execSQL("create table if not exists " + JOURNAL + " ("
                        + ID + " text primary key," // date&id Const.TITLE || date&id Const.TITLE&rnd_place
                        + Const.TIME + " integer);");
                break;
            case MARKERS:
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
                ContentValues row = new ContentValues();
                row.put(Const.TITLE, App.context.getString(R.string.no_collections));
                db.insert(COLLECTIONS, null, row);
                break;
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    @Override
    public synchronized void close() {
        db.close();
        super.close();
    }

    public long insert(String table, String nullColumnHack, ContentValues row) {
        return db.insert(table, nullColumnHack, row);
    }

    public long insert(String table, ContentValues row) {
        return db.insert(table, null, row);
    }

    public int update(String table, ContentValues row, String whereClause, String[] whereArgs) {
        return db.update(table, row, whereClause, whereArgs);
    }

    public int update(String table, ContentValues row, String whereClause, String whereArg) {
        return db.update(table, row, whereClause, new String[]{whereArg});
    }

    public Cursor query(String table, String[] columns, String selection, String[] selectionArgs) {
        return db.query(table, columns, selection, selectionArgs, null, null, null);
    }

    public Cursor query(String table, String[] columns, String selection, String selectionArg) {
        return db.query(table, columns, selection, new String[]{selectionArg}, null, null, null);
    }

    public Cursor query(String table, String[] columns) {
        return db.query(table, columns, null, null, null, null, null);
    }

    public Cursor query(String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy) {
        return db.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
    }

    public Cursor rawQuery(String query) {
        return db.rawQuery(query, null);
    }

    public int delete(String table) {
        return db.delete(table, null, null);
    }

    public int delete(String table, String whereClause, String whereArg) {
        return db.delete(table, whereClause, new String[]{whereArg});
    }

    public int delete(String table, String whereClause, String[] whereArgs) {
        return db.delete(table, whereClause, whereArgs);
    }
}
