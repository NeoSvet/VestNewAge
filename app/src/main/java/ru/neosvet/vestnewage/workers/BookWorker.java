package ru.neosvet.vestnewage.workers;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import ru.neosvet.html.PageParser;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.ErrorUtils;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.MyException;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.fragment.BookFragment;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.LoaderHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.model.BookModel;

public class BookWorker extends Worker {
    private final long SIZE_EMPTY_BASE = 24576L;
    private final Context context;
    private final List<String> title = new ArrayList<>();
    private final List<String> links = new ArrayList<>();
    private final Lib lib;
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
        BOOK = getInputData().getString(Const.TASK).equals(BookModel.class.getSimpleName());
        String error;
        ErrorUtils.setData(getInputData());
        if (BOOK) {
            ProgressHelper.setBusy(true);
            ProgressHelper.postProgress(new Data.Builder()
                    .putBoolean(Const.START, true)
                    .build());
        }
        try {
            if (getInputData().getBoolean(Const.OTKR, false)) { //загрузка Посланий за 2004-2015
                ProgressHelper.setMax(137); //август 2004 - декабрь 2015
                String s = loadListUcoz(true, UcozType.OLD);
                ProgressHelper.postProgress(new Data.Builder()
                        .putBoolean(Const.FINISH, true)
                        .putBoolean(Const.OTKR, true)
                        .putString(Const.TITLE, s)
                        .build());
                SharedPreferences pref = context.getSharedPreferences(BookFragment.class.getSimpleName(), Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = pref.edit();
                editor.putBoolean(Const.OTKR, true);
                editor.apply();
                LoaderHelper.postCommand(context, LoaderHelper.STOP_WITH_NOTIF, null);
                return Result.success();
            }
            if (BOOK) { // book section
                boolean kat = getInputData().getBoolean(Const.KATRENY, false);
                boolean fromOtkr = getInputData().getBoolean(Const.FROM_OTKR, false);
                DateHelper d = DateHelper.initToday(context);
                if (!kat && fromOtkr) { //все Послания
                    max = 146; //август 2004 - сентябрь 2016
                    loadListUcoz(false, UcozType.OLD); //обновление старых Посланий
                } else if (kat) //Катрены
                    max = (d.getYear() - 2016) * 12 + d.getMonth() - 1;
                else //только новые Послания
                    max = 9; //январь-сентябрь 2016
                cur = 0;
                String s;
                if (kat)
                    s = loadPoems();
                else
                    s = loadTolkovaniya();
                ProgressHelper.postProgress(new Data.Builder()
                        .putBoolean(Const.FINISH, true)
                        .putString(Const.TITLE, s)
                        .build());
                return Result.success();
            }
            //LoaderHelper
            if (!LoaderHelper.start)
                return Result.success();
            loadTolkovaniya();
            loadPoems();
            if (!LoaderHelper.start)
                return Result.success();
            DateHelper d = DateHelper.initToday(context);
            ProgressHelper.setMax((d.getYear() - 2016) * 12);
            loadListUcoz(true, UcozType.PART1);
            loadListUcoz(true, UcozType.PART2);
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            ErrorUtils.setError(e);
            error = e.getMessage();
        }
        if (BOOK) {
            ProgressHelper.postProgress(new Data.Builder()
                    .putBoolean(Const.FINISH, true)
                    .putString(Const.ERROR, error)
                    .build());
        } else {
            LoaderHelper.postCommand(context, LoaderHelper.STOP_WITH_NOTIF, error);
            return Result.failure();
        }
        return Result.failure();
    }

    private String loadTolkovaniya() throws Exception {
        if (lib.isMainSite())
            return loadListBook(Const.SITE + Const.PRINT + "tolkovaniya" + Const.HTML);
        throw new MyException(context.getString(R.string.site_not_available));
    }

    private enum UcozType {
        OLD,  //август 2004 - декабрь 2015
        PART1, //январь 2016 - декабрь 2020
        PART2 //январь 2021 - ...
    }

    private String loadListUcoz(boolean withDialog, UcozType type) throws Exception {
        String name = "", s, url = "http://neosvet.ucoz.ru/databases_vna";
        switch (type) {
            case OLD:
                url += "/list.txt";
                break;
            case PART1:
                url += "/list_new.txt";
                break;
            case PART2:
                url += "2/list.txt";
                break;
        }
        BufferedInputStream in = new BufferedInputStream(lib.getStream(url));
        final String path = lib.getDBFolder() + "/";
        File f;
        long l;
        //list format:
        //01.05 delete [time] - при необходимости список обновить
        //02.05 [length] - проверка целостности
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        List<String> list = new ArrayList<>();
        while ((s = br.readLine()) != null) {
            if (isCancelled()) {
                br.close();
                in.close();
                return name;
            }
            name = s.substring(0, s.indexOf(" "));
            f = new File(path + name);
            if (f.exists()) {
                l = Long.parseLong(s.substring(s.lastIndexOf(" ") + 1));
                if (s.contains("delete")) {
                    if (f.lastModified() < l) {
                        list.add(name);
                        f.delete();
                    }
                } else if (l != f.length()) {
                    list.add(name);
                    if (f.length() != SIZE_EMPTY_BASE)
                        f.delete();
                }
            } else {
                list.add(name);
            }
        }
        br.close();
        in.close();

        url = url.substring(0, url.lastIndexOf("/") + 1);
        DataBase dataBase;
        boolean isTitle;
        HashMap<String, Integer> ids = new HashMap<>();
        int n, id;
        String v;
        final long time = System.currentTimeMillis();
        DateHelper d;

        for (String item : list) {
            if (max > 0) {
                ProgressHelper.postProgress(new Data.Builder()
                        .putBoolean(Const.DIALOG, true)
                        .putInt(Const.PROG, ProgressHelper.getProcent(cur, max))
                        .build());
                cur++;
            } else {
                d = DateHelper.parse(context, item);
                ProgressHelper.setMessage(d.getMonthString() + " " + d.getYear());
            }

            dataBase = new DataBase(context, item);
            isTitle = true;
            in = new BufferedInputStream(lib.getStream(url + item));
            br = new BufferedReader(new InputStreamReader(in, Const.ENCODING), 1000);
            n = 2;
            while ((s = br.readLine()) != null) {
                if (s.equals(Const.AND)) {
                    isTitle = false;
                    s = br.readLine();
                }
                v = br.readLine();

                if (isTitle) {
                    id = dataBase.getPageId(s);
                    if (id == -1)
                        id = (int) dataBase.insert(Const.TITLE, getRow(s, v, time));
                    else
                        dataBase.update(Const.TITLE, getRow(s, v, time), Const.LINK + DataBase.Q, s);
                    ids.put(String.valueOf(n), id);
                    n++;
                } else {
                    dataBase.insert(DataBase.PARAGRAPH, getRow(ids.get(s), v));
                }
            }
            br.close();
            in.close();
            dataBase.close();
            name = item;
            if (withDialog)
                ProgressHelper.upProg();
            if (isCancelled())
                return name;
        }
        return name;
    }

    private ContentValues getRow(String link, String title, long time) {
        ContentValues cv = new ContentValues();
        cv.put(Const.LINK, link);
        cv.put(Const.TITLE, title);
        cv.put(Const.TIME, time);
        return cv;
    }

    private ContentValues getRow(int id, String par) {
        ContentValues cv = new ContentValues();
        cv.put(DataBase.ID, id);
        cv.put(DataBase.PARAGRAPH, par);
        return cv;
    }

    private String loadPoems() throws Exception {
        String s = null;
        int y = DateHelper.initToday(context).getYear();
        for (int i = BOOK ? 2016 : y - 1; i <= y && !isCancelled(); i++) {
            if (lib.isMainSite())
                s = loadListBook(Const.SITE + Const.PRINT + Const.POEMS
                        + "/" + i + Const.HTML);
            else
                s = loadListBook(Const.SITE2 + Const.PRINT + i + Const.HTML);
        }
        return s;
    }

    private String loadListBook(String url) throws Exception {
        PageParser page = new PageParser(context);
        if (lib.isMainSite())
            page.load(url, "page-title");
        else
            page.load(url, "<h2>");

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
            ContentValues cv = new ContentValues();
            cv.put(Const.TIME, System.currentTimeMillis());
            if (dataBase.update(Const.TITLE, cv, DataBase.ID + DataBase.Q, "1") == 0) {
                dataBase.insert(Const.TITLE, cv);
            }
            for (int i = 0; i < title.size(); i++) {
                cv = new ContentValues();
                cv.put(Const.TITLE, title.get(i));
                // пытаемся обновить запись:
                if (dataBase.update(Const.TITLE, cv, Const.LINK + DataBase.Q, links.get(i)) == 0) {
                    // обновить не получилось, добавляем:
                    cv.put(Const.LINK, links.get(i));
                    dataBase.insert(Const.TITLE, cv);
                }
            }
            dataBase.close();
            title.clear();
            links.clear();
        }
    }
}
