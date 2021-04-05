package ru.neosvet.vestnewage.workers;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.LoaderHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.helpers.UnreadHelper;
import ru.neosvet.vestnewage.list.ListItem;
import ru.neosvet.vestnewage.model.CalendarModel;

public class CalendarWorker extends Worker {
    private final Context context;
    private DataBase dataBase;
    private final Lib lib;
    private final List<ListItem> list = new ArrayList<>();

    public CalendarWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
        lib = new Lib(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        boolean CALENDAR = getInputData().getString(Const.TASK).equals(CalendarModel.class.getSimpleName());
        String error;
        try {
            if (CALENDAR) {
                ProgressHelper.setBusy(true);
                ProgressHelper.postProgress(new Data.Builder()
                        .putBoolean(Const.START, true)
                        .build());
                loadListMonth(getInputData().getInt(Const.YEAR, 0),
                        getInputData().getInt(Const.MONTH, 0),
                        getInputData().getBoolean(Const.UNREAD, false));
                ProgressHelper.postProgress(new Data.Builder()
                        .putBoolean(Const.LIST, true)
                        .build());
                return Result.success();
            }
            //LoaderHelper
            if (!LoaderHelper.start)
                return Result.success();
            DateHelper d = DateHelper.initToday(context);
            if (getInputData().getInt(Const.MODE, 0) == LoaderHelper.DOWNLOAD_YEAR) {
                ProgressHelper.setMessage(context.getResources().getString(R.string.download_list));
                int m, y = getInputData().getInt(Const.YEAR, 0);
                if (d.getYear() != y)
                    m = 12;
                else
                    m = d.getMonth();
                ProgressHelper.setMax(m);
                loadListYear(y, m + 1);
            } else { //all calendar
                int max_y = d.getYear() + 1, max_m = 13;
                for (int y = 2016; y < max_y && LoaderHelper.start; y++) {
                    if (y == d.getYear())
                        max_m = d.getMonth() + 1;
                    loadListYear(y, max_m);
                }
            }
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            error = e.getMessage();
        }
        if (CALENDAR) {
            ProgressHelper.postProgress(new Data.Builder()
                    .putBoolean(Const.FINISH, true)
                    .putString(Const.ERROR, error)
                    .build());
        } else {
            LoaderHelper.postCommand(context, LoaderHelper.STOP, error);
            return Result.failure();
        }
        return Result.failure();
    }

    private void loadListYear(int year, int max_m) throws Exception {
        for (int m = 1; m < max_m && LoaderHelper.start; m++) {
            loadListMonth(year, m, false);
            ProgressHelper.upProg();
        }
    }

    public static int getListLink(Context context, int year, int month) throws Exception {
        DateHelper d = DateHelper.putYearMonth(context, year, month);
        DataBase dataBase = new DataBase(context, d.getMY());
        Cursor curTitle = dataBase.query(Const.TITLE, new String[]{Const.LINK});
        int k = 0;
        if (curTitle.moveToFirst()) {
            // пропускаем первую запись - там только дата изменения списка
            String link;
            File file = LoaderHelper.getFileList(context);
            BufferedWriter bw = new BufferedWriter(new FileWriter(file));
            while (curTitle.moveToNext()) {
                link = curTitle.getString(0);
                bw.write(link);
                k++;
                bw.newLine();
                bw.flush();
            }
            bw.close();
        }
        curTitle.close();
        dataBase.close();
        return k;
    }

    private void loadListMonth(int year, int month, boolean updateUnread) throws Exception {
        InputStream in = new BufferedInputStream(lib.getStream(Const.SITE
                + "AjaxData/Calendar?year=" + year + "&month=" + month));
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        String s = br.readLine();
        br.close();
        in.close();
        JSONObject json, jsonI;
        JSONArray jsonA;
        String link;
        initDatebase(DateHelper.putYearMonth(context, year, month).getMY());
        json = new JSONObject(s);
        DateHelper d;
        int n;
        for (int i = 0; i < json.names().length() && !ProgressHelper.isCancelled(); i++) {
            s = json.names().get(i).toString();
            jsonI = json.optJSONObject(s);
            n = list.size();
            list.add(new ListItem(s.substring(s.lastIndexOf("-") + 1)));
            if (jsonI == null) { // массив за день (катрен и ещё какой-то текст (послание или статья)
                d = DateHelper.parse(context, s);
                jsonA = json.optJSONArray(s);
                if (jsonA == null)
                    continue;
                for (int j = 0; j < jsonA.length(); j++) {
                    jsonI = jsonA.getJSONObject(j);
                    link = jsonI.getString(Const.LINK) + Const.HTML;
                    if (link.contains(d.toString()))
                        addLink(n, link);
                    else
                        addLink(n, d.toString() + "@" + link);
                }
            } else { // один элемент за день (один или несколько катренов)
                link = jsonI.getString(Const.LINK) + Const.HTML;
                addLink(n, link);
                jsonA = jsonI.getJSONObject("data").optJSONArray("titles");
                if (jsonA == null)
                    continue;
                for (int j = 0; j < jsonA.length(); j++) {
                    addLink(n, link + "#" + (j + 2));
                }
            }
        }
        if (dataBase != null) {
            dataBase.close();
            dataBase = null;
        }
        if (ProgressHelper.isCancelled()) {
            list.clear();
            return;
        }
        if (updateUnread) {
            DateHelper dItem = DateHelper.putYearMonth(context, year, month);
            UnreadHelper unread = new UnreadHelper(context);
            for (int i = 0; i < list.size(); i++) {
                for (int j = 0; j < list.get(i).getCount(); j++) {
                    dItem.setDay(Integer.parseInt(list.get(i).getTitle()));
                    unread.addLink(list.get(i).getLink(j), dItem);
                }
            }
            unread.setBadge();
            unread.close();
        }
        list.clear();
    }


    private void initDatebase(String name) throws Exception {
        if (dataBase != null)
            dataBase.close();
        dataBase = new DataBase(context, name);
        ContentValues cv = new ContentValues();
        cv.put(Const.TIME, System.currentTimeMillis());
        if (dataBase.update(Const.TITLE, cv, DataBase.ID + DataBase.Q, "1") == 0) {
            dataBase.insert(Const.TITLE, cv);
        }
    }

    private void addLink(int n, String link) throws Exception {
        if (list.get(n).getCount() > 0) {
            for (int i = 0; i < list.get(n).getCount(); i++) {
                if (list.get(n).getLink(i).contains(link))
                    return;
            }
        }
        list.get(n).addLink(link);
        ContentValues cv = new ContentValues();
        cv.put(Const.LINK, link);
        // пытаемся обновить запись:
        if (dataBase.update(Const.TITLE, cv, Const.LINK + DataBase.Q, link) == 0) {
            // обновить не получилось, добавляем:
            if (link.contains("@"))
                cv.put(Const.TITLE, link.substring(9));
            else
                cv.put(Const.TITLE, link);
            dataBase.insert(Const.TITLE, cv);
        }
    }
}
