package ru.neosvet.vestnewage.workers;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.ProgressModel;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.fragment.SiteFragment;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.model.CalendarModel;
import ru.neosvet.vestnewage.model.LoaderModel;
import ru.neosvet.vestnewage.model.SiteModel;
import ru.neosvet.vestnewage.model.SummaryModel;
import ru.neosvet.vestnewage.service.CheckService;

public class LoaderWorker extends Worker {
    private Context context;
    private ProgressModel model;
    private Lib lib;
    private Request.Builder builderRequest;
    private OkHttpClient client;

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
        String err, name;
        name = getInputData().getString(ProgressModel.NAME);
        model = ProgressModel.getModelByName(name);
        try {
            if (name.equals(CheckService.class.getSimpleName())) {
                downloadList();
                return Result.success();
            }
            if (name.equals(CalendarModel.class.getSimpleName())) {
                int k = CalendarWolker.getListLink(context,
                        getInputData().getInt(Const.YEAR, 0),
                        getInputData().getInt(Const.MONTH, 0));
                setProgMax(k);
                downloadList();
                return Result.success();
            }
            if (name.equals(SummaryModel.class.getSimpleName())) {
                int k = SummaryWorker.getListLink(context);
                setProgMax(k);
                downloadList();
                return Result.success();
            }
            if (name.equals(SiteModel.class.getSimpleName())) {
                int k = SiteWorker.getListLink(context, lib.getFileByName(
                        getInputData().getString(Const.FILE)).toString());
                setProgMax(k);
                downloadList();
                return Result.success();
            }
            if (isCancelled())
                return Result.success();
            initClient();
            int mode = getInputData().getInt(Const.MODE, 0);
            Data.Builder result = new Data.Builder()
                    .putInt(Const.MODE, mode);
            switch (mode) {
                case LoaderModel.DOWNLOAD_ALL:
                    download(LoaderModel.ALL);
                    break;
                case LoaderModel.DOWNLOAD_ID:
                    int p = getInputData().getInt(Const.SELECT, 0);
                    download(p);
                    break;
                case LoaderModel.DOWNLOAD_YEAR:
                    downloadYear(getInputData().getInt(Const.YEAR, 0));
                    break;
                default:
                    String link = getInputData().getString(Const.LINK);
                    if (mode == LoaderModel.DOWNLOAD_PAGE ||
                            mode == LoaderModel.DOWNLOAD_PAGE_WITH_STYLE) {
                        downloadStyle(mode == LoaderModel.DOWNLOAD_PAGE_WITH_STYLE);
                        downloadPage(link, true);
                    } else { //file
                        downloadFile(link, getInputData().getString(Const.FILE));
                    }
                    result.putString(Const.LINK, link);
                    break;
            }
            return Result.success(result.build());
        } catch (Exception e) {
            e.printStackTrace();
            err = e.getMessage();
            Lib.LOG("LoaderWolker error: " + err);
        }
        return Result.failure(new Data.Builder()
                .putString(Const.ERROR, err)
                .build());
    }

    private void downloadYear(int year) throws Exception {
        DateHelper d = DateHelper.initToday(context);
        int k = 12;
        if (year == d.getYear())
            k -= d.getMonth();
        setProgMax(k);
        for (int m = 0; m < k && !isCancelled(); m++) {
            CalendarWolker.getListLink(context, year, m);
            downloadList();
            upProg();
        }
    }

    private void downloadList() throws Exception {
        File file = LoaderModel.getFileList(context);
        if (!file.exists())
            return;
        BufferedReader br = new BufferedReader(new FileReader(file));
        String s;
        while ((s = br.readLine()) != null && !isCancelled()) {
            downloadPage(s, false);
        }
        br.close();
        file.delete();
    }

    private void download(int id) throws Exception {
        if (isCancelled()) return;
        // подсчёт количества страниц:
        int k = 0;
        if (id == LoaderModel.ALL || id == R.id.nav_main) {
            k = SiteWorker.getListLink(context, lib.getFileByName(SiteFragment.MAIN).toString());
            k += SiteWorker.getListLink(context, lib.getFileByName(SiteFragment.MEDIA).toString());
        }
        if (id == LoaderModel.ALL || id == R.id.nav_book)
            k += workWithBook(true);
        setProgMax(k);
        // загрузка страниц:
        if (id == LoaderModel.ALL || id == R.id.nav_main)
            downloadList();
        if (isCancelled())
            return;
        if (id == LoaderModel.ALL || id == R.id.nav_book)
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
        d = DateHelper.putYearMonth(context, 2016, 1);
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
        int k = curTitle.getCount();
        curTitle.close();
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
                upProg();
            }
        }
        curTitle.close();
        dataBase.close();
    }

    private boolean downloadPage(String link, boolean singlePage) throws Exception {
        // если singlePage=true, значит страницу страницу перезагружаем, а счетчики обрабатываем
        DataBase dataBase = new DataBase(context, link);
        if (!singlePage && dataBase.existsPage(link))
            return false;
//        if (model == null) { //CheckService
//            CheckService.progress.postValue(new Data.Builder()
//                    .putString(Const.LINK, link)
//                    .build());
//        }
        if (model != null) {
            model.postProgress(new Data.Builder()
                    .putInt(Const.DIALOG, LoaderModel.DIALOG_MSG)
                    .putString(Const.LINK, link)
                    .build());
        }
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
        dataBase.close();
        return true;
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

    private void downloadFile(String url, String file) {
        try {
            File f = new File(file);
            //if(f.exists()) f.delete();
            builderRequest.url(url);
            Response response = client.newCall(builderRequest.build()).execute();
            InputStream in = new BufferedInputStream(response.body().byteStream());
            OutputStream out = new FileOutputStream(f, false);
            byte[] buf = new byte[1024];
            int i;
            while ((i = in.read(buf)) > 0) {
                out.write(buf, 0, i);
                out.flush();
            }
            out.close();
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
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
            bwLight.close();
            bwDark.close();
        }
    }

    private void initClient() throws Exception {
        builderRequest = new Request.Builder();
        builderRequest.header(Const.USER_AGENT, context.getPackageName());
        builderRequest.header("Referer", Const.SITE);
        client = lib.createHttpClient();
    }

    private void setProgMax(int max) {
        model.postProgress(new Data.Builder()
                .putInt(Const.DIALOG, LoaderModel.DIALOG_UPDATE)
                .putString(Const.MSG, context.getResources().getString(R.string.download_materials))
                .putInt(Const.MAX, max).build());
    }

    private void upProg() {
        model.postProgress(new Data.Builder()
                .putInt(Const.DIALOG, LoaderModel.DIALOG_UP)
                .build());
    }
}
