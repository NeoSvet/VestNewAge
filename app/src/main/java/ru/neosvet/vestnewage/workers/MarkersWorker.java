package ru.neosvet.vestnewage.workers;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.support.annotation.NonNull;

import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.model.MarkersModel;

public class MarkersWorker extends Worker {
    private Context context;

    public MarkersWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        String error;
        try {
            boolean export = getInputData().getBoolean(Const.MODE, false);
            String file = getInputData().getString(Const.FILE);
            if (export)
                doExport(Uri.parse(file));
            else
                doImport(Uri.parse(file));

            ProgressHelper.postProgress(new Data.Builder()
                    .putBoolean(Const.FINISH, true)
                    .putBoolean(Const.MODE, export)
                    .putString(Const.FILE, file)
                    .build());
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            error = e.getMessage();
            Lib.LOG("MarkersWorker error: " + error);
        }
        ProgressHelper.postProgress(new Data.Builder()
                .putBoolean(Const.FINISH, true)
                .putString(Const.ERROR, error)
                .build());
        return Result.failure();
    }

    private void doExport(Uri file) throws Exception {
        DataBase dbMarker = new DataBase(context, DataBase.MARKERS);
        SQLiteDatabase db = dbMarker.getWritableDatabase();
        int i1, i2, i3;
        Cursor cursor = db.query(DataBase.COLLECTIONS, null, null, null, null, null, DataBase.ID);
        //DocumentFile file = folder.createFile(DataBase.MARKERS, DataBase.MARKERS);
        OutputStream outStream = context.getContentResolver().openOutputStream(file);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(outStream, Const.ENCODING));
        if (cursor.moveToFirst()) {
            i1 = cursor.getColumnIndex(DataBase.ID);
            i2 = cursor.getColumnIndex(Const.TITLE);
            i3 = cursor.getColumnIndex(DataBase.MARKERS);
            do {
                bw.write(cursor.getString(i1) + Const.N);
                bw.write(cursor.getString(i2) + Const.N);
                bw.write(cursor.getString(i3) + Const.N);
                bw.flush();
            } while (cursor.moveToNext());
        }
        cursor.close();
        bw.write(Const.AND + Const.N);
        cursor = db.query(DataBase.MARKERS, null, null, null, null, null, DataBase.ID);
        int i4, i5;
        if (cursor.moveToFirst()) {
            i1 = cursor.getColumnIndex(DataBase.ID);
            i2 = cursor.getColumnIndex(Const.PLACE);
            i3 = cursor.getColumnIndex(Const.LINK);
            i4 = cursor.getColumnIndex(Const.DESCTRIPTION);
            i5 = cursor.getColumnIndex(DataBase.COLLECTIONS);
            do {
                bw.write(cursor.getString(i1) + Const.N);
                bw.write(cursor.getString(i2) + Const.N);
                bw.write(cursor.getString(i3) + Const.N);
                bw.write(cursor.getString(i4) + Const.N);
                bw.write(cursor.getString(i5) + Const.N);
                bw.flush();
            } while (cursor.moveToNext());
        }
        cursor.close();
        bw.close();
        outStream.close();
        cursor.close();
        db.close();
        dbMarker.close();
    }

    private void doImport(Uri file) throws Exception {
        DataBase dbMarker = new DataBase(context, DataBase.MARKERS);
        SQLiteDatabase db = dbMarker.getWritableDatabase();
        InputStream inputStream = context.getContentResolver().openInputStream(file);
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, Const.ENCODING), 1000);
        String s;
        int id, nid;
        Cursor cursor;
        ContentValues cv;

        //определение новых id для подборок
        HashMap<Integer, Integer> hC = new HashMap<>();
        while ((s = br.readLine()) != null) {
            if (s.equals(Const.AND))
                break;
            id = Integer.parseInt(s);
            s = br.readLine();//title
            cursor = db.query(DataBase.COLLECTIONS, new String[]{DataBase.ID},
                    Const.TITLE + DataBase.Q, new String[]{s}, null, null, null);
            if (cursor.moveToFirst()) {
                nid = cursor.getInt(0);
            } else {
                cv = new ContentValues();
                cv.put(Const.TITLE, s);
                nid = (int) db.insert(DataBase.COLLECTIONS, null, cv);
            }
            hC.put(id, nid);
            cursor.close();
            br.readLine();  //markers
        }
        //определение новых id для закладок
        HashMap<Integer, Integer> hM = new HashMap<>();
        String p, d;
        while ((s = br.readLine()) != null) {
            id = Integer.parseInt(s);
            p = br.readLine();
            s = br.readLine();
            d = br.readLine();
            br.readLine(); //col
            cursor = db.query(DataBase.MARKERS, new String[]{DataBase.ID},
                    Const.PLACE + DataBase.Q + DataBase.AND + Const.LINK +
                            DataBase.Q + DataBase.AND + Const.DESCTRIPTION + DataBase.Q,
                    new String[]{p, s, d}, null, null, null);
            if (cursor.moveToFirst()) {
                nid = cursor.getInt(0);
            } else {
                cv = new ContentValues();
                cv.put(Const.PLACE, p);
                cv.put(Const.LINK, s);
                cv.put(Const.DESCTRIPTION, d);
                nid = (int) db.insert(DataBase.MARKERS, null, cv);
            }
            hM.put(id, nid);
            cursor.close();
        }
        br.close();
        inputStream.close();
        //изменение id в подборках
        inputStream = context.getContentResolver().openInputStream(file);
        br = new BufferedReader(new InputStreamReader(inputStream, Const.ENCODING), 1000);
        File f = new File(context.getFilesDir() + File.separator + DataBase.MARKERS);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), Const.ENCODING));
        while ((s = br.readLine()) != null) {
            if (s.equals(Const.AND))
                break;
            id = Integer.parseInt(s);
            bw.write(hC.get(id) + Const.N);
            br.readLine(); //title
            s = br.readLine();
            bw.write(getNewId(hM, DataBase.getList(s)) + Const.N); //markers
            bw.flush();
        }
        //изменение id в закладках
        bw.write(s + Const.N);
        while ((s = br.readLine()) != null) {
            id = Integer.parseInt(s);
            bw.write(hM.get(id) + Const.N);
            br.readLine(); //place
            br.readLine(); //link
            br.readLine(); //des
            s = br.readLine();
            bw.write(getNewId(hC, DataBase.getList(s)) + Const.N); //col
            bw.flush();
        }
        bw.close();
        br.close();
        inputStream.close();
        hC.clear();
        hM.clear();
        //совмещение подборок
        br = new BufferedReader(new InputStreamReader(new FileInputStream(f), Const.ENCODING), 1000);
        while ((s = br.readLine()) != null) {
            if (s.equals(Const.AND))
                break;
            cursor = db.query(DataBase.COLLECTIONS, new String[]{DataBase.MARKERS},
                    DataBase.ID + DataBase.Q, new String[]{s},
                    null, null, null);
            if (cursor.moveToFirst()) {
                p = br.readLine();
                cv = new ContentValues();
                cv.put(DataBase.MARKERS, combineIds(cursor.getString(0), DataBase.getList(p)));
                db.update(DataBase.COLLECTIONS, cv, DataBase.ID + DataBase.Q, new String[]{s});
            }
            cursor.close();
        }
        //совмещение закладок
        while ((s = br.readLine()) != null) {
            cursor = db.query(DataBase.MARKERS, new String[]{DataBase.COLLECTIONS},
                    DataBase.ID + DataBase.Q, new String[]{s},
                    null, null, null);
            if (cursor.moveToFirst()) {
                p = br.readLine();
                cv = new ContentValues();
                cv.put(DataBase.COLLECTIONS, combineIds(cursor.getString(0), DataBase.getList(p)));
                db.update(DataBase.MARKERS, cv, DataBase.ID + DataBase.Q, new String[]{s});
            }
            cursor.close();
        }
        br.close();
        f.delete();
    }

    private String combineIds(String ids, String[] m) {
        StringBuilder b;
        if (ids == null) {
            b = new StringBuilder();
            for (int i = 0; i < m.length; i++) {
                b.append(Const.COMMA);
                b.append(m[i]);
            }
            b.delete(0, 1);
        } else {
            b = new StringBuilder(ids);
            ids = DataBase.closeList(ids);
            for (int i = 0; i < m.length; i++) {
                if (!ids.contains(DataBase.closeList(m[i]))) {
                    b.append(Const.COMMA);
                    b.append(m[i]);
                }
            }
        }
        return b.toString();
    }

    private String getNewId(HashMap<Integer, Integer> h, String[] m) throws Exception {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < m.length; i++) {
            b.append(h.get(Integer.parseInt(m[i])));
            b.append(Const.COMMA);
        }
        b.delete(b.length() - 1, b.length());
        return b.toString();
    }
}
