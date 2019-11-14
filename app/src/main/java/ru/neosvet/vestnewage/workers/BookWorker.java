package ru.neosvet.vestnewage.workers;

import android.content.ContentValues;
import android.content.Context;
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
import ru.neosvet.utils.ProgressModel;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.model.BookModel;
import ru.neosvet.vestnewage.model.LoaderModel;

public class BookWorker extends Worker {
    private Context context;
    private ProgressModel model;
    private List<String> title = new ArrayList<String>();
    private List<String> links = new ArrayList<String>();
    private Lib lib;
    private Data progUp;

    public BookWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
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
        String error, name;
        name = getInputData().getString(ProgressModel.NAME);
        model = ProgressModel.getModelByName(name);
        try {
            if (name.equals(BookModel.class.getSimpleName())) {
                if (getInputData().getBoolean(Const.OTKR, false)) {
                    name = loadListOtrk(true);
                    model.postProgress(new Data.Builder()
                            .putBoolean(Const.FINISH, true)
                            .putString(Const.TITLE, name)
                            .build());
                    return Result.success();
                }
                boolean kat = getInputData().getBoolean(Const.KATRENY, false);
                if (!kat && getInputData().getBoolean(Const.FROM_OTKR, false))
                    loadListOtrk(false); //если вкладка Послания и Откровения были загружены, то их тоже надо обновить
                name = loadListBook(kat);
                model.postProgress(new Data.Builder()
                        .putBoolean(Const.FINISH, true)
                        .putString(Const.TITLE, name)
                        .build());
                return Result.success();
            }
            //loader
            progUp = new Data.Builder()
                    .putInt(Const.DIALOG, LoaderModel.DIALOG_UP)
                    .build();
            loadListBook(false);
            loadListBook(true);
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            error = e.getMessage();
            Lib.LOG("BookWolker error: " + error);
        }
        model.postProgress(new Data.Builder()
                .putBoolean(Const.FINISH, true)
                .putString(Const.ERROR, error)
                .build());
        return Result.failure();
    }

    private String loadListOtrk(boolean withDialog) throws Exception {
        if (withDialog) {
            model.postProgress(new Data.Builder()
                    .putInt(Const.DIALOG, LoaderModel.DIALOG_SHOW)
                    .putString(Const.MSG, context.getResources().getString(R.string.start))
                    .putInt(Const.MAX, 137)
                    .build());
        }
        final String path = lib.getDBFolder() + "/";
        File f;
        String s, name = "";
        long l;
        BufferedInputStream in = new BufferedInputStream(lib.getStream("http://neosvet.ucoz.ru/databases_vna/list.txt"));
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
        DateHelper d = DateHelper.putYearMonth(context, 2004, 8);
        DataBase dataBase;
        SQLiteDatabase db;
        ContentValues cv;
        boolean isTitle;
        final long time = System.currentTimeMillis();
        while (d.getYear() < 2016) {
            name = d.getMY();
            if (withDialog) {
                model.postProgress(new Data.Builder()
                        .putInt(Const.DIALOG, LoaderModel.DIALOG_MSG)
                        .putString(Const.MSG, d.getMonthString() + " " + d.getYear())
                        .build());
            }
            f = new File(path + name);
            if (!f.exists()) {
                dataBase = new DataBase(context, name);
                db = dataBase.getWritableDatabase();
                isTitle = true;
                in = new BufferedInputStream(lib.getStream("http://neosvet.ucoz.ru/databases_vna/" + name));
                br = new BufferedReader(new InputStreamReader(in, "cp1251"), 1000);
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
                dataBase.close();
            }
            d.changeMonth(1);
            if (withDialog) {
                model.postProgress(new Data.Builder()
                        .putInt(Const.DIALOG, LoaderModel.DIALOG_UP)
                        .build());
            }
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
                        if (progUp != null)
                            model.postProgress(progUp);
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
            dataBase.close();
            title.clear();
            links.clear();
        }
    }
}
