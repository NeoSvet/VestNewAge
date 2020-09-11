package ru.neosvet.vestnewage.workers;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.PageParser;
import ru.neosvet.vestnewage.fragment.BookFragment;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.LoaderHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.model.BookModel;

public class BookWorker extends Worker {
    private Context context;
    private List<String> title = new ArrayList<String>();
    private List<String> links = new ArrayList<String>();
    private Lib lib;
    private int cur, max;
    private boolean BOOK;

    public BookWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
        lib = new Lib(context);
    }

    private boolean isCancelled() {
        if (BOOK)
            return ProgressHelper.isCancelled();
        return !LoaderHelper.start;
    }

    @NonNull
    @Override
    public Result doWork() {
        String error;
        BOOK = getInputData().getString(Const.TASK).equals(BookModel.class.getSimpleName());
        if (BOOK) {
            ProgressHelper.setBusy(true);
            ProgressHelper.postProgress(new Data.Builder()
                    .putBoolean(Const.START, true)
                    .build());
        }
        try {
            if (getInputData().getBoolean(Const.OTKR, false)) { //загрузка Посланий за 2004-2015
                String s = loadListUcoz(true, false);
                ProgressHelper.postProgress(new Data.Builder()
                        .putBoolean(Const.FINISH, true)
                        .putBoolean(Const.OTKR, true)
                        .putString(Const.TITLE, s)
                        .build());
                SharedPreferences pref = context.getSharedPreferences(BookFragment.class.getSimpleName(), Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = pref.edit();
                editor.putBoolean(Const.OTKR, true);
                editor.apply();
                LoaderHelper.postCommand(context, LoaderHelper.STOP, null);
                return Result.success();
            }
            if (BOOK) { // book section
                boolean kat = getInputData().getBoolean(Const.KATRENY, false);
                boolean fromOtkr = getInputData().getBoolean(Const.FROM_OTKR, false);
                DateHelper d = DateHelper.initToday(context);
                if (!kat && fromOtkr) { //все Послания
                    max = 146; //август 2004 - сентябрь 2016
                    loadListUcoz(false, false); //обновление старых Посланий
                } else if (kat) //Катрены
                    max = (d.getYear() - 2016) * 12 + d.getMonth() - 1;
                else //только новые Послания
                    max = 9; //январь-сентябрь 2016
                cur = 0;
                String s;
                if (kat)
                    s = loadPoems();
                else
                    s = loadListBook("tolkovaniya");
                ProgressHelper.postProgress(new Data.Builder()
                        .putBoolean(Const.FINISH, true)
                        .putString(Const.TITLE, s)
                        .build());
                return Result.success();
            }
            //LoaderHelper
            if (!LoaderHelper.start)
                return Result.success();
            loadListBook("tolkovaniya");
            loadPoems();
            if (!LoaderHelper.start)
                return Result.success();
            loadListUcoz(true, true);
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            error = e.getMessage();
        }
        if (BOOK) {
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

    private String loadListUcoz(boolean withDialog, boolean bNew) throws Exception {
        int m;
        DateHelper d = null;
        if (withDialog) {
            if (bNew) {
                d = DateHelper.initToday(context);
                m = (d.getYear() - 2016) * 12;
            } else //старые Послания
                m = 137; //август 2004 - декабрь 2015
            ProgressHelper.setMax(m);
        }
        final String path = lib.getDBFolder() + "/";
        File f;
        String s, name = "";
        long l;
        BufferedInputStream in = new BufferedInputStream(lib.getStream("http://neosvet.ucoz.ru/databases_vna/list" +
                (bNew ? "_new" : "") + ".txt"));
        //list format:
        //01.05 delete [time] - при необходимости список обновить
        //02.05 [length] - проверка целостности
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        while ((s = br.readLine()) != null) {
            if (isCancelled()) {
                br.close();
                in.close();
                return name;
            }
            f = new File(path + s.substring(0, s.indexOf(" ")));
            if (f.exists()) {
                l = Long.parseLong(s.substring(s.lastIndexOf(" ") + 1));
                if (s.contains("delete"))
                    if (f.lastModified() < l) f.delete();
                else if (f.length() != l) f.delete();
            }
        }
        br.close();
        in.close();
        if (bNew) {
            if (d == null)
                d = DateHelper.initToday(context);
            m = d.getYear();
            d = DateHelper.putYearMonth(context, 2016, 1);
        } else {
            m = 2016;
            d = DateHelper.putYearMonth(context, 2004, 8);
        }
        DataBase dataBase;
        SQLiteDatabase db;
        ContentValues cv;
        boolean isTitle;
        final long time = System.currentTimeMillis();
        while (d.getYear() < m) {
            name = d.getMY();
            if (max > 0) {
                ProgressHelper.postProgress(new Data.Builder()
                        .putBoolean(Const.DIALOG, true)
                        .putInt(Const.PROG, ProgressHelper.getProcent(cur, max))
                        .build());
                cur++;
            } else
                ProgressHelper.setMessage(d.getMonthString() + " " + d.getYear());
            f = new File(path + name);
            if (!f.exists()) {
                dataBase = new DataBase(context, name);
                db = dataBase.getWritableDatabase();
                isTitle = true;
                in = new BufferedInputStream(lib.getStream("http://neosvet.ucoz.ru/databases_vna/" + name));
                br = new BufferedReader(new InputStreamReader(in, Const.ENCODING), 1000);
                while ((s = br.readLine()) != null) {
                    if (s.equals(Const.AND)) {
                        isTitle = false;
                        s = br.readLine();
                    }
                    cv = new ContentValues();
                    if (isTitle) {
                        cv.put(Const.LINK, s);
                        cv.put(Const.TITLE, br.readLine());
                        cv.put(Const.TIME, time);
                        db.insert(Const.TITLE, null, cv);
                    } else {
                        cv.put(DataBase.ID, Integer.parseInt(s));
                        cv.put(DataBase.PARAGRAPH, br.readLine());
                        db.insert(DataBase.PARAGRAPH, null, cv);
                    }
                }
                br.close();
                in.close();
                db.close();
                dataBase.close();
            }
            d.changeMonth(1);
            if (withDialog)
                ProgressHelper.upProg();
            if (isCancelled())
                return name;
        }
        return name;
    }

    private String loadPoems() throws Exception {
        String s = null;
        for (int i = 2016; i <= DateHelper.initToday(context).getYear() && !isCancelled(); i++) {
            s = loadListBook(Const.POEMS + "/" + i);
        }
        return s;
    }

    private String loadListBook(String url) throws Exception {
        url = Const.SITE + Const.PRINT + url + Const.HTML;
        PageParser page = new PageParser(context);
        page.load(url, "page-title");

        String a, s, date1 = "", date2;
        page.getFirstElem();
        do {
            a = page.getLink();
            while (a == null && page.getNextItem() != null) {
                a = page.getLink();
            }
            if (a == null)
                break;
            if (a.length() < 19) continue;
            date2 = DataBase.getDatePage(a);
            if (date1.equals(""))
                date1 = date2;
            else if (!date2.equals(date1)) {
                saveData(date1);
                if (max > 0) {
                    ProgressHelper.postProgress(new Data.Builder()
                            .putBoolean(Const.DIALOG, true)
                            .putInt(Const.PROG, ProgressHelper.getProcent(cur, max))
                            .build());
                    cur++;
                } else
                    ProgressHelper.upProg();
                date1 = date2;
            }
            s = page.getText();
            if (s.contains("(")) //poems
                s = s.substring(0, s.indexOf(" ("));
            title.add(s);
            links.add(a.substring(1));
        } while (page.getNextItem() != null);
        page.clear();
        saveData(date1);
        return date1;
    }

    private void saveData(String date) throws Exception {
        if (title.size() > 0) {
            DataBase dataBase = new DataBase(context, date);
            SQLiteDatabase db = dataBase.getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put(Const.TIME, System.currentTimeMillis());
            if (db.update(Const.TITLE, cv,
                    DataBase.ID + DataBase.Q, new String[]{"1"}) == 0) {
                db.insert(Const.TITLE, null, cv);
            }
            for (int i = 0; i < title.size(); i++) {
                cv = new ContentValues();
                cv.put(Const.TITLE, title.get(i));
                // пытаемся обновить запись:
                if (db.update(Const.TITLE, cv,
                        Const.LINK + DataBase.Q,
                        new String[]{links.get(i)}) == 0) {
                    // обновить не получилось, добавляем:
                    cv.put(Const.LINK, links.get(i));
                    db.insert(Const.TITLE, null, cv);
                }
            }
            db.close();
            dataBase.close();
            title.clear();
            links.clear();
        }
    }
}
