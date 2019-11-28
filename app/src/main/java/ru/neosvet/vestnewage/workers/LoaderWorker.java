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
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
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
    private int k_requests = 0;
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
                return Result.success();
            }
            if (name.equals(CalendarModel.class.getSimpleName())) {
                CalendarWolker.getListLink(context,
                        getInputData().getInt(Const.YEAR, 0),
                        getInputData().getInt(Const.MONTH, 0));
                downloadList();
                return postFinish();
            }
            if (name.equals(SummaryModel.class.getSimpleName())) {
                SummaryWorker.getListLink(context);
                downloadList();
                return postFinish();
            }
            if (name.equals(SiteModel.class.getSimpleName())) {
                SiteWorker.getListLink(context, getInputData().getString(Const.FILE));
                downloadList();
                return postFinish();
            }
            if (!isCancelled())
                switch (getInputData().getInt(Const.MODE, 0)) {
                    case LoaderHelper.DOWNLOAD_ALL:
                        download(LoaderHelper.ALL);
                        break;
                    case LoaderHelper.DOWNLOAD_ID:
                        int p = getInputData().getInt(Const.SELECT, 0);
                        download(p);
                        break;
                    case LoaderHelper.DOWNLOAD_YEAR:
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
                        Data result = new Data.Builder()
                                .putBoolean(Const.FINISH, true)
                                .putString(Const.LINK, link) // use only in CollectionsFragment
                                .build();
                        ProgressHelper.postProgress(result);
                        return Result.success();
                }
            LoaderHelper.postCommand(context, LoaderHelper.STOP, null);
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            error = e.getMessage();
            Lib.LOG("LoaderWolker error: " + error);
        }
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
        ProgressHelper.postProgress(new Data.Builder()
                .putBoolean(Const.FINISH, true)
                .build());
        return Result.success();
    }

    private void downloadYear(int year) throws Exception {
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
            CalendarWolker.getListLink(context, year, m);
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
            ProgressHelper.upProg();
        }
        br.close();
        file.delete();
    }

    private void download(int id) throws Exception {
        if (isCancelled())
            return;
        // подсчёт количества страниц:
        int k = 0;
        if (id == LoaderHelper.ALL || id == R.id.nav_main)
            k = SiteWorker.getListLink(context, lib.getFileByName(SiteFragment.MAIN).toString());
        if (id == LoaderHelper.ALL || id == R.id.nav_book)
            k += workWithBook(true);
        ProgressHelper.setMax(k);
        // загрузка страниц:
        if (id == LoaderHelper.ALL || id == R.id.nav_main)
            downloadList();
        if (isCancelled())
            return;
        if (id == LoaderHelper.ALL || id == R.id.nav_book)
            workWithBook(false);
    }

    private int workWithBook(boolean count) throws Exception {
        List<String> list = new ArrayList<String>();
        File dir = lib.getDBFolder();
        File[] f = dir.listFiles();
        int i;
        for (i = 0; i < f.length && !isCancelled(); i++) {
            if (f[i].getName().length() == 5)
                list.add(f[i].getName());
        }
        int end_year, end_month, k = 0;
        DateHelper d = DateHelper.initToday(context);
        end_month = d.getMonth();
        end_year = d.getYear();
        d = DateHelper.initToday(context);
        d = DateHelper.putYearMonth(context, d.getYear() - 1, 1);
        while (!isCancelled()) {
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
        checkRequests();
        String line, s = link;
        final String par = "</p>";
        if (link.contains("#")) {
            s = s.substring(0, s.indexOf("#"));
            if (link.contains("?")) s += link.substring(link.indexOf("?"));
        }
        builderRequest.url(Const.SITE2 + s + Const.PRINT);
        Response response = client.newCall(builderRequest.build()).execute();
        BufferedReader br = new BufferedReader(response.body().charStream(), 1000);
        SQLiteDatabase db = dataBase.getWritableDatabase();
        ContentValues cv;
        boolean begin = false;
        int id = 0;
        while ((line = br.readLine()) != null) {
            if (begin) {
                if (line.contains("<!--/row-->") || line.contains("<h1>")) {
                    break;
                } else if (line.contains("<")) {
                    line = line.trim();
                    if (line.length() < 7) continue;
                    line = line.replace("<br />", Const.BR).replace("color", "cvet");
                    if (line.contains("<p")) {
                        while (!line.contains(par)) {
                            line += br.readLine();
                        }
                    }
                    if (line.contains("iframe")) {
                        if (!line.contains("</iframe"))
                            line += br.readLine();
                        if (line.contains("?"))
                            s = line.substring(line.indexOf("video/") + 6,
                                    line.indexOf("?"));
                        else
                            s = line.substring(line.indexOf("video/") + 6,
                                    line.indexOf("\"", 65));
                        s = "<a href=\"https://vimeo.com/" + s + "\">" +
                                context.getResources().getString
                                        (R.string.video_on_vimeo) + "</a>";
                        if (line.contains("center"))
                            line = "<center>" + s + "</center>";
                        else line = s;
                    } else if (line.contains("noind")) { // объединяем подпись в один абзац
                        s = br.readLine();
                        while (s.contains("noind")) {
                            line += s;
                            s = br.readLine();
                        }
                        while (line.indexOf(par) < line.lastIndexOf(par)) {
                            line = line.substring(0, line.indexOf(par)) + Const.BR +
                                    line.substring(line.indexOf("\">", line.indexOf(par)) + 2);
                        }
                    } else {
                        while (line.indexOf(par) < line.lastIndexOf(par)) {
                            // своей Звезды!</p>(<a href="/2016/29.02.16.html">Послание от 29.02.16</a>)
                            s = line.substring(0, line.indexOf(par) + par.length());
                            cv = new ContentValues();
                            cv.put(DataBase.ID, id);
                            cv.put(DataBase.PARAGRAPH, s);
                            db.insert(DataBase.PARAGRAPH, null, cv);
                            line = line.substring(s.length());
                        }
                    }
                    cv = new ContentValues();
                    cv.put(DataBase.ID, id);
                    cv.put(DataBase.PARAGRAPH, line);
                    db.insert(DataBase.PARAGRAPH, null, cv);
                }
            } else if (line.contains("<h1")) {
                if (link.contains("#")) {// ссылка на последующий текст на странице
                    // узнаем его номер:
                    id = Integer.parseInt(link.substring(link.indexOf("#") + 1));
                    // мотаем до него:
                    while (id > 1) {
                        line = br.readLine();
                        if (line.contains("<h1"))
                            id--;
                    }
                }
                Cursor cursor = db.query(Const.TITLE, new String[]{DataBase.ID, Const.TITLE},
                        Const.LINK + DataBase.Q, new String[]{link}
                        , null, null, null);
                if (cursor.moveToFirst()) {
                    id = cursor.getInt(0);
                    s = cursor.getString(1);
                } else s = "";
                cursor.close();
                cv = new ContentValues();
                cv.put(Const.TIME, System.currentTimeMillis());
                if (id == 0) { // id не найден, материала нет - добавляем
                    cv.put(Const.TITLE, getTitle(line, dataBase.getName()));
                    cv.put(Const.LINK, link);
                    id = (int) db.insert(Const.TITLE, null, cv);
                    //обновляем дату изменения списка:
                    cv = new ContentValues();
                    cv.put(Const.TIME, System.currentTimeMillis());
                    db.update(Const.TITLE, cv, DataBase.ID +
                            DataBase.Q, new String[]{"1"});
                } else { // id найден, значит материал есть
                    if (s.contains("/")) // в заголовке ссылка, необходимо заменить
                        cv.put(Const.TITLE, getTitle(line, dataBase.getName()));
                    //обновляем дату загрузки материала
                    db.update(Const.TITLE, cv, DataBase.ID +
                            DataBase.Q, new String[]{String.valueOf(id)});
                    //удаляем содержимое материала
                    db.delete(DataBase.PARAGRAPH, DataBase.ID +
                            DataBase.Q, new String[]{String.valueOf(id)});
                }
                br.readLine();
                begin = true;
            } else if (line.contains("counter") && singlePage) { // счетчики
                sendCounter(line);
            }
        }
        br.close();
        response.close();
        db.close();
        dataBase.close();
        if (!name.contains(LoaderModel.TAG)) {
            ProgressHelper.postProgress(new Data.Builder()
                    .putBoolean(Const.DIALOG, true)
                    .putString(Const.MSG, link)
                    .build());
        }
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

    private void checkRequests() { //TODO no work?
        k_requests++;
        if (k_requests == 20) {
            long now = System.currentTimeMillis();
            k_requests = 0;
            if (now - time_requests < DateHelper.SEC_IN_MILLS) {
                try {
                    Thread.sleep(DateHelper.SEC_IN_MILLS);
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

    private void sendCounter(String line) {
        int i = 0;
        while ((i = line.indexOf("img src", i)) > -1) {
            i += 9;
            final String link_counter = line.substring(i, line.indexOf("\"", i));
            new Thread(new Runnable() {
                public void run() {
                    try {
                        builderRequest.url(link_counter);
                        Response response = client.newCall(builderRequest.build()).execute();
                        response.close();
                    } catch (Exception ex) {
                    }
                }
            }).start();
        }
    }

    private void downloadStyle(boolean replaceStyle) throws Exception {
        final File fLight = lib.getFile(Const.LIGHT);
        final File fDark = lib.getFile(Const.DARK);
        if (!fLight.exists() || !fDark.exists() || replaceStyle) {
            String line = "";
            int i;
            builderRequest.url(Const.SITE2 + "org/otk/tpl/otk/css/style-print.css");
            Response response = client.newCall(builderRequest.build()).execute();
            BufferedReader br = new BufferedReader(response.body().charStream(), 1000);
            BufferedWriter bwLight = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fLight)));
            BufferedWriter bwDark = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fDark)));
            for (i = 0; i < 7; i++) {
                br.readLine();
            }
            while ((line = br.readLine()) != null) {
                if (line.contains("P B {")) { //correct bold
                    br.readLine(); //font-weight:600;
                    br.readLine(); //}
                    line = br.readLine();
                }
                line = line.replace("333333", "333").replace("#333", "#ccc");
                bwLight.write(line + Const.N);
                if (line.contains("#000")) {
                    line = line.replace("000000", "000").replace("#000", "#fff");
                } else
                    line = line.replace("#fff", "#000");
                bwDark.write(line + Const.N);
                if (line.contains("body")) {
                    line = "    padding-left: 5px;\n    padding-right: 5px;";
                    bwLight.write(line + Const.N);
                    bwDark.write(line + Const.N);
                } else if (line.contains("print2")) {
                    line = br.readLine().replace("8pt/9pt", "12pt");
                    bwLight.write(line + Const.N);
                    bwDark.write(line + Const.N);
                }
                bwLight.flush();
                bwDark.flush();
            }
            br.close();
            response.close();
            bwLight.close();
            bwDark.close();
        }
    }
}
