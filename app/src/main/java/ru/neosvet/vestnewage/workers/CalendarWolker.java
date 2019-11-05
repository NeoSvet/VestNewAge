package ru.neosvet.vestnewage.workers;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.ProgressModel;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.UnreadHelper;
import ru.neosvet.vestnewage.list.ListItem;
import ru.neosvet.vestnewage.task.LoaderTask;

public class CalendarWolker extends Worker {
    private Context context;
    public static final String TAG = "Calendar", DAY = "Day",
            MONTH = "Month", YEAR = "Year", UNREAD = "Unread";
    private transient DataBase dataBase;
    private transient SQLiteDatabase db;
    private transient Lib lib;
    private List<ListItem> data = new ArrayList<ListItem>();
    private ProgressModel model;

    public CalendarWolker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
        lib = new Lib(context);
    }

    public boolean isCancelled() {
        if (model != null)
            return false;
        else
            return !model.inProgress;
    }

    @NonNull
    @Override
    public Result doWork() {
        String err = "";
        model = ProgressModel.getModelByName(getInputData().getString(ProgressModel.NAME));
        try {
            downloadCalendar(getInputData().getInt(YEAR, 0),
                    getInputData().getInt(MONTH, 0),
                    getInputData().getBoolean(UNREAD, false));
            if (isCancelled())
                return Result.success();
            publishProgress(0);
            downloadMonth(getInputData().getInt(YEAR, 0),
                    getInputData().getInt(MONTH, 0));
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            err = e.getMessage();
            Lib.LOG("CalendarWolker error: " + err);
        }
        Data data = new Data.Builder()
                .putString(ProgressModel.ERROR, err)
                .build();
        return Result.failure(data);
    }

    private void publishProgress(int p) {
        Data data = new Data.Builder()
                .putString(Const.TASK, TAG)
                .putInt(DAY, p)
                .build();
        model.setProgress(data);
    }

    private void downloadMonth(int year, int month) throws Exception {
        DateHelper d = DateHelper.putYearMonth(context, year, month);
        DataBase dataBase = new DataBase(context, d.getMY());
        SQLiteDatabase db = dataBase.getWritableDatabase();
        Cursor curTitle = db.query(DataBase.TITLE, new String[]{DataBase.LINK},
                null, null, null, null, null);
        if (curTitle.moveToFirst()) {
            // пропускаем первую запись - там только дата изменения списка
            LoaderTask loader = new LoaderTask(context);
            loader.initClient();
            loader.downloadStyle(false);
            int n;
            String link;
            while (curTitle.moveToNext()) {
                link = curTitle.getString(0);
                if (loader.downloadPage(link, false)) {
                    n = link.lastIndexOf("/") + 1;
                    n = Integer.parseInt(link.substring(n, n + 2));
                    publishProgress(n);
                }
                if (isCancelled()) {
                    curTitle.close();
                    dataBase.close();
                    return;
                }
            }
        }
        curTitle.close();
        dataBase.close();
    }

    public void downloadCalendar(int year, int month, boolean updateUnread) throws Exception {
        try {
            InputStream in = new BufferedInputStream(lib.getStream(Const.SITE + "?json&year="
                    + year + "&month=" + month));
            BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
            String s = br.readLine();
            br.close();
            if (isCancelled())
                return;

            JSONObject json, jsonI;
            JSONArray jsonA;
            String link;
            json = new JSONObject(s);
            int n;
            for (int i = 0; i < json.names().length(); i++) {
                s = json.names().get(i).toString();
                jsonI = json.optJSONObject(s);
                n = data.size();
                data.add(new ListItem(s.substring(s.lastIndexOf("-") + 1)));
                if (jsonI == null) { // несколько материалов за день
                    jsonA = json.optJSONArray(s);
                    for (int j = 0; j < jsonA.length(); j++) {
                        jsonI = jsonA.getJSONObject(j);
                        link = jsonI.getString(DataBase.LINK) + Const.HTML;
                        addLink(n, link);
                    }
                } else { // один материал за день
                    link = jsonI.getString(DataBase.LINK) + Const.HTML;
                    addLink(n, link);
                    jsonI = jsonI.getJSONObject("data");
                    if (jsonI != null) {
                        if (jsonI.has("title2")) {
                            if (!jsonI.getString("title2").equals(""))
                                addLink(n, link + "#2");
                        }
                    }
                }
            }
            dataBase.close();
            dataBase = null;
            if (isCancelled()) {
                data.clear();
                return;
            }

            if (updateUnread) {
                DateHelper dItem = DateHelper.putYearMonth(context, year, month);
                UnreadHelper unread = new UnreadHelper(context);
                for (int i = 0; i < data.size(); i++) {
                    for (int j = 0; j < data.get(i).getCount(); j++) {
                        dItem.setDay(Integer.parseInt(data.get(i).getTitle()));
                        unread.addLink(data.get(i).getLink(j), dItem);
                    }
                }
                unread.close();
            }
            data.clear();
        } catch (org.json.JSONException e) {
        }
    }

    private void initDatebase(String link) {
        if (dataBase != null) return;
        dataBase = new DataBase(context, link);
        db = dataBase.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(DataBase.TIME, System.currentTimeMillis());
        if (db.update(DataBase.TITLE, cv,
                DataBase.ID + DataBase.Q, new String[]{"1"}) == 0) {
            db.insert(DataBase.TITLE, null, cv);
        }
    }

    private void addLink(int n, String link) {
        if (data.get(n).getCount() > 0) {
            String s = link.substring(link.lastIndexOf("/"));
            for (int i = 0; i < data.get(n).getCount(); i++) {
                if (data.get(n).getLink(i).contains(s))
                    return;
            }
        }
        data.get(n).addLink(link);
        initDatebase(link);
        ContentValues cv = new ContentValues();
        cv.put(DataBase.LINK, link);
        // пытаемся обновить запись:
        if (db.update(DataBase.TITLE, cv,
                DataBase.LINK + DataBase.Q,
                new String[]{link}) == 0) {
            // обновить не получилось, добавляем:
            cv.put(DataBase.TITLE, link);
            db.insert(DataBase.TITLE, null, cv);
        }
    }
}
