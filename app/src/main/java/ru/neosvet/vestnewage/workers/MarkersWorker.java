package ru.neosvet.vestnewage.workers;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
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
import ru.neosvet.vestnewage.App;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.storage.MarkersStorage;

public class MarkersWorker extends Worker {
    private MarkersStorage dbMarker;

    public MarkersWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        dbMarker = new MarkersStorage();
    }

    @NonNull
    @Override
    public Result doWork() {
        String error;
        try {
            boolean isExport = getInputData().getBoolean(Const.MODE, false);
            String file = getInputData().getString(Const.FILE);
            if (isExport)
                doExport(Uri.parse(file));
            else
                doImport(Uri.parse(file));

            ProgressHelper.postProgress(new Data.Builder()
                    .putBoolean(Const.FINISH, true)
                    .putBoolean(Const.MODE, isExport)
                    .putString(Const.FILE, file)
                    .build());
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            error = e.getMessage();
        }
        ProgressHelper.postProgress(new Data.Builder()
                .putBoolean(Const.FINISH, true)
                .putString(Const.ERROR, error)
                .build());
        return Result.failure();
    }

    private void doExport(Uri file) throws Exception {
        int i1, i2, i3;
        Cursor cursor = dbMarker.getCollections(DataBase.ID);
        //DocumentFile file = folder.createFile(DataBase.MARKERS, DataBase.MARKERS);
        OutputStream outStream = App.context.getContentResolver().openOutputStream(file);
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
        cursor = dbMarker.getMarkers();
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
        dbMarker.close();
    }

    private void doImport(Uri file) throws Exception {
        InputStream inputStream = App.context.getContentResolver().openInputStream(file);
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, Const.ENCODING), 1000);
        String s;
        int id, nid;
        Cursor cursor;
        ContentValues row;

        //определение новых id для подборок
        HashMap<Integer, Integer> hC = new HashMap<>();
        while ((s = br.readLine()) != null) {
            if (s.equals(Const.AND))
                break;
            id = Integer.parseInt(s);
            s = br.readLine();//title
            cursor = dbMarker.getMarkersListByTitle(s);
            if (cursor.moveToFirst()) {
                nid = cursor.getInt(0);
            } else {
                row = new ContentValues();
                row.put(Const.TITLE, s);
                nid = (int) dbMarker.insertCollection(row);
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
            nid = dbMarker.foundMarker(new String[]{p, s, d});
            if (nid == -1) {
                row = new ContentValues();
                row.put(Const.PLACE, p);
                row.put(Const.LINK, s);
                row.put(Const.DESCTRIPTION, d);
                nid = (int) dbMarker.insertMarker(row);
            }
            hM.put(id, nid);
        }
        br.close();
        inputStream.close();
        //изменение id в подборках
        inputStream = App.context.getContentResolver().openInputStream(file);
        br = new BufferedReader(new InputStreamReader(inputStream, Const.ENCODING), 1000);
        File f = Lib.getFileS(DataBase.MARKERS);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), Const.ENCODING));
        while ((s = br.readLine()) != null) {
            if (s.equals(Const.AND))
                break;
            id = Integer.parseInt(s);
            bw.write(hC.get(id) + Const.N);
            br.readLine(); //title
            s = br.readLine();
            bw.write(getNewId(hM, dbMarker.getList(s)) + Const.N); //markers
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
            bw.write(getNewId(hC, dbMarker.getList(s)) + Const.N); //col
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
            cursor = dbMarker.getMarkersList(s);
            if (cursor.moveToFirst()) {
                p = br.readLine();
                row = new ContentValues();
                row.put(DataBase.MARKERS, combineIds(cursor.getString(0), dbMarker.getList(p)));
                dbMarker.updateCollection(Integer.parseInt(s), row);
            }
            cursor.close();
        }
        //совмещение закладок
        while ((s = br.readLine()) != null) {
            cursor = dbMarker.getMarkerCollections(s);
            if (cursor.moveToFirst()) {
                p = br.readLine();
                row = new ContentValues();
                row.put(DataBase.COLLECTIONS, combineIds(cursor.getString(0), dbMarker.getList(p)));
                dbMarker.updateMarker(s, row);
            }
            cursor.close();
        }
        br.close();
        f.delete();

        dbMarker.close();
    }

    private String combineIds(String ids, String[] m) {
        StringBuilder b;
        if (ids == null) {
            b = new StringBuilder();
            for (String s : m) {
                b.append(Const.COMMA);
                b.append(s);
            }
            b.delete(0, 1);
        } else {
            b = new StringBuilder(ids);
            ids = dbMarker.closeList(ids);
            for (String s : m) {
                if (!ids.contains(dbMarker.closeList(s))) {
                    b.append(Const.COMMA);
                    b.append(s);
                }
            }
        }
        return b.toString();
    }

    private String getNewId(HashMap<Integer, Integer> h, String[] m) throws Exception {
        StringBuilder b = new StringBuilder();
        for (String s : m) {
            b.append(h.get(Integer.parseInt(s)));
            b.append(Const.COMMA);
        }
        b.delete(b.length() - 1, b.length());
        return b.toString();
    }
}
