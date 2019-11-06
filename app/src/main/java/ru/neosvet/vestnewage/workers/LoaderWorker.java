package ru.neosvet.vestnewage.workers;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.ProgressModel;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.fragment.SiteFragment;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.model.LoaderModel;

public class LoaderWorker extends Worker {
    private Context context;
    public static final String TAG = "loader";
    private ProgressModel model;
    private Lib lib;
    private String msg;
    private int prog = 0;

    public LoaderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
        lib = new Lib(context);
    }

    private boolean isCancelled() {
        if (model == null)
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
            startTimer();
            switch (getInputData().getInt(Const.MODE, 0)) {
                case LoaderModel.DOWNLOAD_ALL:
                    refreshLists(-1);
                    downloadAll(-1);
                    break;
                case LoaderModel.DOWNLOAD_ID:
                    int p = getInputData().getInt(Const.SELECT, 0);
                    refreshLists(p);
                    downloadAll(p);
                    break;
                case LoaderModel.DOWNLOAD_YEAR:
                    downloadYear(getInputData().getInt(Const.YEAR, 0));
                    break;
            }
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            err = e.getMessage();
            Lib.LOG("LoaderWolker error: " + err);
        }
        Data data = new Data.Builder()
                .putString(Const.ERROR, err)
                .build();
        return Result.failure(data);
    }

    private void downloadYear(int year) throws Exception {
        msg = context.getResources().getString(R.string.download_list);
        //refresh list:
        DateHelper d = DateHelper.initToday(context);
        int k = 12;
        if (year == d.getYear())
            k -= d.getMonth();
        publishProgress(k);
        CalendarTask t2 = new CalendarTask((Activity) context);
        for (int m = 0; m < k && start; m++) {
            t2.downloadCalendar(year, m, false);
            prog++;
        }
        //open list:
        List<String> list = new ArrayList<String>();
        File dir = lib.getDBFolder();
        File[] f = dir.listFiles();
        int i;
        for (i = 0; i < f.length && start; i++) {
            if (f[i].getName().length() == 5)
                list.add(f[i].getName());
        }
        int end_month = k;
        k = 0;
        //count pages:
        d = DateHelper.putYearMonth(context, year, 1);
        while (start) {
            for (i = 0; i < list.size(); i++) {
                if (list.contains(d.getMY())) {
                    k += countBookList(d.getMY());
                    break;
                }
            }
            if (d.getMonth() == end_month)
                break;
            d.changeMonth(1);
        }
        //download:
        prog = 0;
        msg = context.getResources().getString(R.string.download_materials);
        publishProgress(k);
        d = DateHelper.putYearMonth(context, year, 1);
        while (start) {
            for (i = 0; i < list.size(); i++) {
                if (list.contains(d.getMY())) {
                    downloadBookList(d.getMY());
                    break;
                }
            }
            if (d.getMonth() == end_month)
                break;
            d.changeMonth(1);
        }
    }

    private void refreshLists(int p) throws Exception {
        msg = context.getResources().getString(R.string.download_list);
        // подсчёт количества списков:
        int k = 0;
        DateHelper d = DateHelper.initToday(context);
        if (p == -1 || p == R.id.nav_book) {
            k = (d.getYear() - 2016) * 12 + d.getMonth() - 1; //poems from 02.16
            k += 9; // poslaniya (01.16-09.16)
        }
        if (p == -1) {
            k += (d.getYear() - 2016) * 12 + d.getMonth(); // calendar from 01.16
            k += 4; // main, news, media and rss
        } else if (p == R.id.nav_main) // main, news, media
            k = 3;
        publishProgress(k);
        //загрузка списков
        if (p == -1) {
            SummaryTask t1 = new SummaryTask((MainActivity) context);
            t1.downloadList();
            prog++;
        }
        if (!start) return;

        if (p == -1 || p == R.id.nav_main) {
            SiteTask t4 = new SiteTask((MainActivity) context);
            String[] url = new String[]{
                    Const.SITE,
                    Const.SITE + "novosti.html",
                    Const.SITE + "media.html"
            };
            String[] file = new String[]{
                    getFile(SiteFragment.MAIN).toString(),
                    getFile(SiteFragment.NEWS).toString(),
                    getFile(SiteFragment.MEDIA).toString()
            };
            for (int i = 0; i < url.length && start; i++) {
                t4.downloadList(url[i]);
                t4.saveList(file[i]);
                prog++;
            }
        }
        if (!start) return;

        if (p == -1) {
            d = DateHelper.initToday(context);
            CalendarTask t2 = new CalendarTask((Activity) context);
            int max_y = d.getYear() + 1, max_m = 13;
            for (int y = 2016; y < max_y && start; y++) {
                if (y == d.getYear())
                    max_m = d.getMonth() + 1;
                for (int m = 0; m < max_m && start; m++) {
                    t2.downloadCalendar(y, m, false);
                    prog++;
                }
            }
        }
        if (!start) return;

        if (p == -1 || p == R.id.nav_book) {
            BookTask t3 = new BookTask((MainActivity) context);
            t3.downloadData(true, this);
            prog++;
            t3.downloadData(false, this);
            prog++;
        }
    }

    private void downloadAll(int p) throws Exception {
        prog = 0;
        msg = context.getResources().getString(R.string.download_materials);
        if (!start) return;
        File[] d;
        if (p == -1) { //download all
            d = new File[]{
                    null,
                    getFile(SiteFragment.MAIN),
                    getFile(SiteFragment.MEDIA)
            };
        } else { // download it
            if (p == R.id.nav_main) {
                d = new File[]{
                        getFile(SiteFragment.MAIN),
                        getFile(SiteFragment.MEDIA)
                };
            } else //R.id.nav_book
                d = new File[]{null};
        }
        // подсчёт количества страниц:
        int k = 0;
        for (int i = 0; i < d.length && start; i++) {
            if (d[i] == null)
                k += workWithBook(true);
            else
                k += workWithList(d[i], true);
        }
        publishProgress(k);
        // загрузка страниц:
        for (int i = 0; i < d.length && start; i++) {
            if (d[i] == null)
                workWithBook(false);
            else
                workWithList(d[i], false);
        }
    }

    private int workWithBook(boolean count) throws Exception {
        List<String> list = new ArrayList<String>();
        File dir = lib.getDBFolder();
        File[] f = dir.listFiles();
        int i;
        for (i = 0; i < f.length && start; i++) {
            if (f[i].getName().length() == 5)
                list.add(f[i].getName());
        }
        int end_year, end_month, k = 0;
        DateHelper d = DateHelper.initToday(context);
        end_month = d.getMonth();
        end_year = d.getYear();
        d = DateHelper.putYearMonth(context, 2016, 1);
        while (start) {
            for (i = 0; i < list.size(); i++) {
                if (list.contains(d.getMY())) {
                    if (count)
                        k += countBookList(d.getMY());
                    else
                        downloadBookList(d.getMY());
                    break;
                }
            }
            if (d.getYear() == end_year && d.getMonth() == end_month)
                break;
            d.changeMonth(1);
        }
        return k;
    }

    private int countBookList(String name) throws Exception {
        DataBase dataBase = new DataBase(context, name);
        SQLiteDatabase db = dataBase.getWritableDatabase();
        Cursor curTitle = db.query(DataBase.TITLE, null, null, null, null, null, null);
        int k = curTitle.getCount();
        curTitle.close();
        dataBase.close();
        return k;
    }

    private void downloadBookList(String name) throws Exception {
        DataBase dataBase = new DataBase(context, name);
        SQLiteDatabase db = dataBase.getWritableDatabase();
        Cursor curTitle = db.query(DataBase.TITLE, new String[]{DataBase.LINK},
                null, null, null, null, null);
        if (curTitle.moveToFirst()) {
            // пропускаем первую запись - там только дата изменения списка
            while (curTitle.moveToNext()) {
                downloadPage(curTitle.getString(0), false);
                prog++;
            }
        }
        curTitle.close();
        dataBase.close();
    }

    private void startTimer() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    while (start && prog < max) {
                        Thread.sleep(DateHelper.SEC_IN_MILLS);
                        publishProgress();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private int workWithList(File file, boolean count) throws Exception {
        downloadStyle(false);
        BufferedReader br = new BufferedReader(new FileReader(file));
        String s;
        int k = 0;
        while ((s = br.readLine()) != null && start) {
            if (s.contains(Const.HTML)) {
                if (count)
                    k++;
                else {
                    downloadPage(s, false);
                    prog++;
                }
            }
        }
        br.close();
        return k;
    }

    public void upProg() {
        prog++;
    }
}
