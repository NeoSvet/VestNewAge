package ru.neosvet.vestnewage.workers;

import android.content.ContentValues;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.ErrorUtils;
import ru.neosvet.utils.NeoClient;
import ru.neosvet.vestnewage.App;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.LoaderHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.helpers.UnreadHelper;
import ru.neosvet.vestnewage.list.ListItem;
import ru.neosvet.vestnewage.presenter.CalendarPresenter;
import ru.neosvet.vestnewage.storage.PageStorage;

public class CalendarWorker extends Worker {
    private PageStorage storage;
    private final List<ListItem> list = new ArrayList<>();

    public CalendarWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        boolean CALENDAR = getInputData().getString(Const.TASK).equals(CalendarPresenter.class.getSimpleName());
        String error;
        ErrorUtils.setData(getInputData());
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
            DateHelper d = DateHelper.initToday();
            if (getInputData().getInt(Const.MODE, 0) == LoaderHelper.DOWNLOAD_YEAR) {
                ProgressHelper.setMessage(App.context.getString(R.string.download_list));
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
            ErrorUtils.setError(e);
            error = e.getMessage();
        }
        if (CALENDAR) {
            ProgressHelper.postProgress(new Data.Builder()
                    .putBoolean(Const.FINISH, true)
                    .putString(Const.ERROR, error)
                    .build());
        } else {
            LoaderHelper.postCommand(LoaderHelper.STOP, error);
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

    private void loadListMonth(int year, int month, boolean updateUnread) throws Exception {
        InputStream in = NeoClient.getStream(Const.SITE
                + "AjaxData/Calendar/" + year + "-" + month + ".json");
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        String s = br.readLine();
        br.close();
        in.close();
        if (s.length() < 20)
            return;

        JSONObject json = new JSONObject(s);
        json = json.getJSONObject("calendarData");
        if (json == null || json.names() == null)
            return;
        initDatebase(DateHelper.putYearMonth(year, month).getMY());
        JSONObject jsonI;
        JSONArray jsonA;
        String link;
        DateHelper d;
        int n;
        for (int i = 0; i < json.names().length() && !ProgressHelper.isCancelled(); i++) {
            s = json.names().get(i).toString();
            jsonI = json.optJSONObject(s);
            n = list.size();
            list.add(new ListItem(s.substring(s.lastIndexOf("-") + 1)));
            if (jsonI == null) { // массив за день (катрен и ещё какой-то текст (послание или статья)
                d = DateHelper.parse(s);
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
        if (storage != null) {
            storage.close();
            storage = null;
        }
        if (ProgressHelper.isCancelled()) {
            list.clear();
            return;
        }
        if (updateUnread) {
            DateHelper dItem = DateHelper.putYearMonth(year, month);
            UnreadHelper unread = new UnreadHelper();
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

    private void initDatebase(String name) {
        if (storage != null)
            storage.close();
        storage = new PageStorage(name);
        ContentValues row = new ContentValues();
        row.put(Const.TIME, System.currentTimeMillis());
        if (!storage.updateTitle(1, row))
            storage.insertTitle(row);
    }

    private void addLink(int n, String link) {
        if (list.get(n).getCount() > 0) {
            for (int i = 0; i < list.get(n).getCount(); i++) {
                if (list.get(n).getLink(i).contains(link))
                    return;
            }
        }
        list.get(n).addLink(link);
        ContentValues row = new ContentValues();
        row.put(Const.LINK, link);
        // пытаемся обновить запись:
        if (!storage.updateTitle(link, row)) {
            // обновить не получилось, добавляем:
            if (link.contains("@"))
                row.put(Const.TITLE, link.substring(9));
            else
                row.put(Const.TITLE, link);
            storage.insertTitle(row);
        }
    }
}
