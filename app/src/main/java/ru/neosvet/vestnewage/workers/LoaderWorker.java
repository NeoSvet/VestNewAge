package ru.neosvet.vestnewage.workers;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStreamWriter;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.PageParser;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.fragment.SiteFragment;
import ru.neosvet.vestnewage.helpers.CheckHelper;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.LoaderHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.model.CalendarModel;
import ru.neosvet.vestnewage.model.LoaderModel;
import ru.neosvet.vestnewage.model.SiteModel;
import ru.neosvet.vestnewage.model.SummaryModel;

public class LoaderWorker extends Worker {
    private Context context;
    private Lib lib;
    private Request.Builder builderRequest;
    private OkHttpClient client;
    private int cur, max, k_requests = 0;
    private long time_requests = 0;
    private String name;

    public LoaderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
        lib = new Lib(context);
    }

    private boolean isCancelled() {
        if (name.equals(LoaderModel.TAG))
            return !LoaderModel.inProgress;
        if (name.equals(LoaderHelper.TAG))
            return !LoaderHelper.start;
        return ProgressHelper.isCancelled();
    }

    @NonNull
    @Override
    public Result doWork() {
        LoaderModel.inProgress = true;
        String error;
        name = getInputData().getString(Const.TASK);
        try {
            time_requests = System.currentTimeMillis();
            builderRequest = new Request.Builder();
            builderRequest.header(Const.USER_AGENT, context.getPackageName());
            builderRequest.header("Referer", Const.SITE);
            client = Lib.createHttpClient();
            if (name.equals(CheckHelper.class.getSimpleName())) {
                downloadList();
                CheckHelper.postCommand(context, false);
                LoaderModel.inProgress = false;
                return Result.success();
            }
            if (name.equals(CalendarModel.class.getSimpleName())) {
                max = CalendarWorker.getListLink(context,
                        getInputData().getInt(Const.YEAR, 0),
                        getInputData().getInt(Const.MONTH, 0));
                downloadList();
                return postFinish();
            }
            if (name.equals(SummaryModel.class.getSimpleName())) {
                max = SummaryWorker.getListLink(context);
                downloadList();
                return postFinish();
            }
            if (name.equals(SiteModel.class.getSimpleName())) {
                max = SiteWorker.getListLink(context, getInputData().getString(Const.FILE));
                downloadList();
                return postFinish();
            }
            if (!isCancelled())
                switch (getInputData().getInt(Const.MODE, 0)) {
                    case LoaderHelper.DOWNLOAD_ALL:
                        downloadStyle(false);
                        download(LoaderHelper.ALL);
                        break;
                    case LoaderHelper.DOWNLOAD_ID:
                        downloadStyle(false);
                        int p = getInputData().getInt(Const.SELECT, 0);
                        download(p);
                        break;
                    case LoaderHelper.DOWNLOAD_YEAR:
                        downloadStyle(false);
                        downloadYear(getInputData().getInt(Const.YEAR, 0));
                        break;
                    case LoaderHelper.DOWNLOAD_PAGE:
                        ProgressHelper.postProgress(new Data.Builder()
                                .putBoolean(Const.START, true)
                                .build());
                        String link = getInputData().getString(Const.LINK);
                        downloadStyle(getInputData().getBoolean(Const.STYLE, false));
                        if (link != null)
                            downloadPage(link, true);
                        ProgressHelper.postProgress(new Data.Builder()
                                .putBoolean(Const.FINISH, true)
                                .putString(Const.LINK, link) // use only in CollectionsFragment
                                .build());
                        LoaderModel.inProgress = false;
                        return Result.success();
                }
            LoaderHelper.postCommand(context, LoaderHelper.STOP, null);
            LoaderModel.inProgress = false;
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            error = e.getMessage();
        }
        LoaderModel.inProgress = false;
        if (name.equals(CheckHelper.class.getSimpleName())) {
            CheckHelper.postCommand(context, false);
            return Result.failure();
        }
        if (name.equals(LoaderHelper.TAG)) {
            LoaderHelper.postCommand(context, LoaderHelper.STOP, error);
            return Result.failure();
        }
        ProgressHelper.postProgress(new Data.Builder()
                .putBoolean(Const.FINISH, true)
                .putString(Const.ERROR, error)
                .build());
        return Result.failure();
    }

    private Result postFinish() {
        LoaderModel.inProgress = false;
        ProgressHelper.postProgress(new Data.Builder()
                .putBoolean(Const.FINISH, true)
                .build());
        return Result.success();
    }

    private void downloadYear(int year) throws Exception {
        ProgressHelper.setMessage(context.getResources().getString(R.string.start));
        DateHelper d = DateHelper.initToday(context);
        int k;
        if (year == d.getYear())
            k = d.getMonth() + 1;
        else
            k = 13;
        d.setYear(year);
        int m, n = 0;
        for (m = 1; m < k; m++) {
            d.setMonth(m);
            n += countBookList(d.getMY());
        }
        ProgressHelper.setMax(n);
        for (m = 1; m < k && !isCancelled(); m++) {
            CalendarWorker.getListLink(context, year, m);
            downloadList();
        }
    }

    private void downloadList() throws Exception {
        File file = LoaderHelper.getFileList(context);
        if (!file.exists())
            return;
        BufferedReader br = new BufferedReader(new FileReader(file));
        String s;
        while ((s = br.readLine()) != null && !isCancelled()) {
            downloadPage(s, false);
            if (max > 0) {
                cur++;
                ProgressHelper.postProgress(new Data.Builder()
                        .putBoolean(Const.DIALOG, true)
                        .putInt(Const.PROG, ProgressHelper.getProcent(cur, max))
                        .build());
            } else
                ProgressHelper.upProg();
        }
        br.close();
        file.delete();
    }

    private void download(int id) throws Exception {
        if (isCancelled())
            return;
        ProgressHelper.setMessage(context.getResources().getString(R.string.start));
        // подсчёт количества страниц:
        int k = 0;
        if (id == LoaderHelper.ALL || id == R.id.nav_site)
            k = SiteWorker.getListLink(context, lib.getFileByName(SiteFragment.MAIN).toString());
        if (id == LoaderHelper.ALL || id == R.id.nav_book)
            k += workWithBook(true);
        ProgressHelper.setMax(k);
        // загрузка страниц:
        if (id == LoaderHelper.ALL || id == R.id.nav_site) {
            SiteWorker.getListLink(context, lib.getFileByName(SiteFragment.MAIN).toString());
            downloadList();
            SiteWorker.getListLink(context, lib.getFileByName(SiteFragment.NEWS).toString());
            downloadList();
        }
        if (isCancelled())
            return;
        if (id == LoaderHelper.ALL || id == R.id.nav_book)
            workWithBook(false);
    }

    private int workWithBook(boolean count) throws Exception {
        int end_year, end_month, k = 0;
        DateHelper d = DateHelper.initToday(context);
        d.setDay(1);
        end_month = d.getMonth();
        end_year = d.getYear();
        d.setMonth(1);
        d.setYear(end_year - 1);
        while (!isCancelled()) {
            if (count)
                k += countBookList(d.getMY());
            else
                downloadBookList(d.getMY());
            if (d.getYear() == end_year && d.getMonth() == end_month)
                break;
            d.changeMonth(1);
        }
        return k;
    }

    private int countBookList(String name) throws Exception {
        DataBase dataBase = new DataBase(context, name);
        SQLiteDatabase db = dataBase.getWritableDatabase();
        Cursor curTitle = db.query(Const.TITLE, null, null, null, null, null, null);
        int k = curTitle.getCount() - 1;
        curTitle.close();
        db.close();
        dataBase.close();
        return k;
    }

    private void downloadBookList(String name) throws Exception {
        DataBase dataBase = new DataBase(context, name);
        SQLiteDatabase db = dataBase.getWritableDatabase();
        Cursor curTitle = db.query(Const.TITLE, new String[]{Const.LINK},
                null, null, null, null, null);
        if (curTitle.moveToFirst()) {
            // пропускаем первую запись - там только дата изменения списка
            while (curTitle.moveToNext()) {
                downloadPage(curTitle.getString(0), false);
                ProgressHelper.upProg();
            }
        }
        curTitle.close();
        db.close();
        dataBase.close();
    }

    private boolean downloadPage(String link, boolean singlePage) throws Exception {
        // если singlePage=true, значит страницу страницу перезагружаем, а счетчики обрабатываем
        if (name.contains(LoaderModel.TAG))
            ProgressHelper.setMessage(initMessage(link));
        DataBase dataBase = new DataBase(context, link);
        if (!singlePage && dataBase.existsPage(link)) {
            dataBase.close();
            return false;
        }
        if (!singlePage)
            checkRequests();
        String s = link;
        int k = 1;
        if (link.contains("#")) {
            k = Integer.parseInt(s.substring(s.indexOf("#") + 1));
            s = s.substring(0, s.indexOf("#"));
            if (link.contains("?")) s += link.substring(link.indexOf("?"));
        }

        boolean boolArticle = dataBase.getName().equals("00.00");
        PageParser page = new PageParser(context);
        page.load(Const.SITE + Const.PRINT + s, "<!-- Шаблон");

        SQLiteDatabase db = dataBase.getWritableDatabase();
        ContentValues cv;
        int id = 0;

        s = page.getFirstElem();
        do {
            if (page.isHead()) {
                k--;
                if (k == 0) {
                    s = Lib.withOutTags(s);
                    Cursor cursor = db.query(Const.TITLE, new String[]{DataBase.ID, Const.TITLE},
                            Const.LINK + DataBase.Q, new String[]{link}
                            , null, null, null);
                    if (cursor.moveToFirst())
                        id = cursor.getInt(0);
                    else id = 0;
                    cursor.close();
                    cv = new ContentValues();
                    cv.put(Const.TIME, System.currentTimeMillis());

                    if (id == 0) { // id не найден, материала нет - добавляем
                        cv.put(Const.TITLE, getTitle(s, dataBase.getName()));
                        cv.put(Const.LINK, link);
                        id = (int) db.insert(Const.TITLE, null, cv);
                        //обновляем дату изменения списка:
                        cv = new ContentValues();
                        cv.put(Const.TIME, System.currentTimeMillis());
                        db.update(Const.TITLE, cv, DataBase.ID +
                                DataBase.Q, new String[]{"1"});
                    } else { // id найден, значит материал есть
                        //обновляем заголовок
                        cv.put(Const.TITLE, getTitle(s, dataBase.getName()));
                        //обновляем дату загрузки материала
                        db.update(Const.TITLE, cv, DataBase.ID +
                                DataBase.Q, new String[]{String.valueOf(id)});
                        //удаляем содержимое материала
                        db.delete(DataBase.PARAGRAPH, DataBase.ID +
                                DataBase.Q, new String[]{String.valueOf(id)});
                    }
                    s = page.getNextElem();
                }
            }
            if (k == 0 || boolArticle) {
                cv = new ContentValues();
                cv.put(DataBase.ID, id);
                //Lib.LOG("par: " + s);
                cv.put(DataBase.PARAGRAPH, s);
                db.insert(DataBase.PARAGRAPH, null, cv);
            }
            s = page.getNextElem();
        } while (s != null);

        db.close();
        dataBase.close();
        page.clear();
        return true;
    }

    private String initMessage(String s) {
        if (!s.contains("/"))
            return s;
        try {
            s = s.substring(s.lastIndexOf("/") + 1, s.lastIndexOf("."));
            if (s.contains("_"))
                s = s.substring(0, s.length() - 2);
            DateHelper d = DateHelper.parse(context, s);
            return d.getMonthString() + " " + d.getYear();
        } catch (Exception e) {
        }
        return s;
    }

    private void checkRequests() {
        k_requests++;
        if (k_requests == 5) {
            long now = System.currentTimeMillis();
            k_requests = 0;
            if (now - time_requests < DateHelper.SEC_IN_MILLS) {
                try {
                    Thread.sleep(400);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            time_requests = now;
        }
    }

    private String getTitle(String line, String name) {
        line = line.replace("&ldquo;", "“").replace("&rdquo;", "”");
        line = Lib.withOutTags(line);
        if (line.contains(name)) {
            line = line.substring(9);
            if (line.contains(Const.KV_OPEN))
                line = line.substring(line.indexOf(Const.KV_OPEN) + 1, line.length() - 1);
        }
        return line;
    }

    private void downloadStyle(boolean replaceStyle) throws Exception {
        final File fLight = lib.getFile(Const.LIGHT);
        final File fDark = lib.getFile(Const.DARK);
        if (!fLight.exists() || !fDark.exists() || replaceStyle) {
            builderRequest.url(Const.SITE + "_content/BV/style-print.min.css");
            Response response = client.newCall(builderRequest.build()).execute();
            BufferedReader br = new BufferedReader(response.body().charStream(), 1000);
            BufferedWriter bwLight = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fLight)));
            BufferedWriter bwDark = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fDark)));
            String s = br.readLine();
            br.close();
            response.close();
            int u;
            String m[] = s.split("#");
            for (int i = 1; i < m.length; i++) {
                if (i == 1)
                    s = m[i].substring(m[i].indexOf("body"));
                else
                    s = "#" + m[i];
                if (s.contains("P B {")) { //correct bold
                    u = s.indexOf("P B {");
                    s = s.substring(0, u) + s.substring(s.indexOf("}", u) + 1);
                }
                if (s.contains("content"))
                    s = s.replace("15px", "5px");
                else if (s.contains("print2"))
                    s = s.replace("8pt/9pt", "12pt");
                bwLight.write(s);
                s = s.replace("#333", "#ccc");
                if (s.contains("#000"))
                    s = s.replace("#000", "#fff");
                else
                    s = s.replace("#fff", "#000");
                bwDark.write(s);
                bwLight.flush();
                bwDark.flush();
            }
            bwLight.close();
            bwDark.close();
        }
    }
}
