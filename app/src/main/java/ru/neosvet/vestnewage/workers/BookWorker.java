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
        ProgressHelper.setBusy(true);
        String error;
        BOOK = getInputData().getString(Const.TASK).equals(BookModel.class.getSimpleName());
        try {
            if (getInputData().getBoolean(Const.OTKR, false)) {
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
            if (BOOK) {
                boolean kat = getInputData().getBoolean(Const.KATRENY, false);
                boolean fromOtkr = getInputData().getBoolean(Const.FROM_OTKR, false);
                DateHelper d = DateHelper.initToday(context);
                if (fromOtkr)
                    max = (d.getYear() - 2004) * 12 + d.getMonth() - 1;
                else
                    max = (d.getYear() - 2016) * 12 + d.getMonth() - 1;
                cur = 0;
                if (!kat && fromOtkr)
                    loadListUcoz(false, false); //если вкладка Послания и Откровения были загружены, то их тоже надо обновить
                String s = loadListBook(kat);
                ProgressHelper.postProgress(new Data.Builder()
                        .putBoolean(Const.FINISH, true)
                        .putString(Const.TITLE, s)
                        .build());
                return Result.success();
            }
            //LoaderHelper
            if (!LoaderHelper.start)
                return Result.success();
            loadListBook(false);
            loadListBook(true);
            if (!LoaderHelper.start)
                return Result.success();
            loadListUcoz(true, true);
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            error = e.getMessage();
            Lib.LOG("BookWolker error: " + error);
        }
        ProgressHelper.postProgress(new Data.Builder()
                .putBoolean(Const.FINISH, true)
                .putString(Const.ERROR, error)
                .build());
        return Result.failure();
    }

    private String loadListUcoz(boolean withDialog, boolean bNew) throws Exception {
        int m;
        DateHelper d = null;
        if (withDialog) {
            if (bNew) {
                d = DateHelper.initToday(context);
                m = (d.getYear() - 2016) * 12;
            } else
                m = 137;
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
                if (s.contains("delete")) {
                    if (f.lastModified() < l) f.delete();
                } else {
                    if (f.length() < l) f.delete();
                }
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
                        .putString(Const.MSG, ProgressHelper.getProcent(cur, max) + "%")
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

    private String loadListBook(boolean katren) throws Exception {
        String url = Const.SITE + (katren ? Const.POEMS : "tolkovaniya") + Const.PRINT;
        InputStream in = new BufferedInputStream(lib.getStream(url));
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        boolean begin = false;
        int i, n;
        String line, t, s, date1 = "", date2;
        while ((line = br.readLine()) != null) {
            if (isCancelled()) {
                br.close();
                in.close();
                return date1;
            }
            if (!begin)
                begin = line.contains("h2");//razdel
            else if (line.contains("clear"))
                break;
            else if (line.contains(Const.HREF)) {
                if (line.contains("years"))
                    line = line.substring(0, line.indexOf("years"));
                n = 0;
                while (line.indexOf(Const.HREF, n) > -1) {
                    n = line.indexOf(Const.HREF, n) + 7;
                    s = line.substring(n, line.indexOf("'", n));
                    i = s.indexOf(".") + 1;
                    date2 = s.substring(i, i + 5);
                    if (!date2.equals(date1)) {
                        saveData(date1);
                        if (max > 0) {
                            ProgressHelper.postProgress(new Data.Builder()
                                    .putBoolean(Const.DIALOG, true)
                                    .putString(Const.MSG, ProgressHelper.getProcent(cur, max) + "%")
                                    .build());
                            cur++;
                        } else
                            ProgressHelper.upProg();
                        date1 = date2;
                    }
                    t = line.substring(line.indexOf(">", n) + 1, line.indexOf("<", n));
                    if (t.contains("(")) //poems
                        t = t.substring(0, t.indexOf(" ("));
                    title.add(t);
                    links.add(s);
                }
                saveData(date1);
            }
        }
        br.close();
        in.close();
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
