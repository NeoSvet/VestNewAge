package ru.neosvet.vestnewage.task;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.neosvet.ui.ProgressDialog;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.BrowserActivity;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.fragment.SiteFragment;

public class LoaderTask extends AsyncTask<String, Integer, Boolean> implements Serializable {
    public static final String DOWNLOAD_ALL = "all", DOWNLOAD_YEAR = "year", DOWNLOAD_ID = "id",
            DOWNLOAD_PAGE = "page", DOWNLOAD_FILE = "file", DOWNLOAD_PAGE_WITH_STYLE = "style";
    private int max = 1;
    private transient Context context;
    private transient Lib lib;
    private transient ProgressDialog dialog;
    private transient Request.Builder builderRequest;
    private transient OkHttpClient client;
    private int prog = 0;
    private String msg;
    private boolean start = true, all = true;

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);
        if (values.length == 1) { //set max
            max = values[0];
            showDialog();
        } else {
            dialog.setProgress(prog);
            dialog.setMessage(msg);
        }
    }

    public void setAct(Activity act) {
        this.context = act;
        lib = new Lib(act);
        if (start) {
            try {
                initClient();
            } catch (Exception e) {
                e.printStackTrace();
            }
            showDialog();
        }
    }

    public LoaderTask(Context context) {
        this.context = context;
        lib = new Lib(context);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        if (context instanceof MainActivity) {
            msg = context.getResources().getString(R.string.start);
            showDialog();
        }
    }

    private void showDialog() {
        if (dialog != null)
            dialog.dismiss();
        dialog = new ProgressDialog(context, max);
        dialog.setOnCancelListener(new ProgressDialog.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                start = false;
            }
        });
        dialog.show();
        dialog.setMessage(msg);
        dialog.setProgress(prog);
    }

    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);
        if (context instanceof BrowserActivity) {
            ((BrowserActivity) context).finishLoad(result);
        } else if (context instanceof MainActivity) {
            dialog.dismiss();
            if (start) {
                ((MainActivity) context).finishAllLoad(result, all);
                start = false;
            }
        }
    }

    private File getFile(String name) {
        return new File(context.getFilesDir() + name);
    }

    @Override
    protected Boolean doInBackground(String... params) {
        try {
            initClient();
            String mode = params[0];
            switch (mode) {
                case DOWNLOAD_ALL:
                    startTimer();
                    refreshLists(-1);
                    downloadAll(-1);
                    break;
                case DOWNLOAD_ID:
                    startTimer();
                    int p = Integer.parseInt(params[1]);
                    all = false;
                    refreshLists(p);
                    downloadAll(p);
                    break;
                case DOWNLOAD_YEAR:
                    startTimer();
                    downloadYear(Integer.parseInt(params[1]));
                    break;
                case DOWNLOAD_FILE:
                    downloadFile(params[1].replace(Const.SITE, Const.SITE2), params[2]);
                    break;
                default: // download file or page
                    String link = params[1].replace(Const.SITE, Const.SITE2);
                    downloadStyle(mode.equals(DOWNLOAD_PAGE_WITH_STYLE)); // if download/replce style
                    if (!link.equals("")) // download page
                        downloadPage(link, true);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void downloadYear(int year) throws Exception {
        msg = context.getResources().getString(R.string.download_list);
        //refresh list:
        Date d = new Date();
        int m, k = 12;
        if (year == d.getYear())
            k -= d.getMonth() + 1;
        publishProgress(k);
        CalendarTask t2 = new CalendarTask((Activity) context);
        for (m = 0; m < k && start; m++) {
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
        int em = k;
        k = 0;
        m = 0;
        DateFormat df = new SimpleDateFormat("MM.yy");
        //count pages:
        while (start) {
            d = new Date(year, m, 1);
            for (i = 0; i < list.size(); i++) {
                if (list.contains(df.format(d))) {
                    k += countBookList(df.format(d));
                    break;
                }
            }
            if (m == em)
                break;
            m++;
        }
        //download:
        prog = 0;
        msg = context.getResources().getString(R.string.download_materials);
        publishProgress(k);
        m = 0;
        while (start) {
            d = new Date(year, m, 1);
            for (i = 0; i < list.size(); i++) {
                if (list.contains(df.format(d))) {
                    downloadBookList(df.format(d));
                    break;
                }
            }
            if (m == em)
                break;
            m++;
        }
    }

    private void refreshLists(int p) throws Exception {
        msg = context.getResources().getString(R.string.download_list);
        // подсчёт количества списков:
        int k = 0;
        if (p == -1 || p == R.id.nav_book) k = 1;
        if (p == -1) {
            Date d = new Date();
            k += (d.getYear() - 116) * 12 + d.getMonth() + 1; // calendar
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
                    Const.SITE2,
                    Const.SITE2 + "novosti.html",
                    Const.SITE2 + "media.html"
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
            Date d = new Date();
            CalendarTask t2 = new CalendarTask((Activity) context);
            int max_y = d.getYear() + 1, max_m = 12;
            for (int y = 116; y < max_y && start; y++) {
                if (y == max_y - 1)
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
            //TODO: progress download list
            t3.downloadData(true);
            t3.downloadData(false);
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
        int sy, sm, ey, em, k = 0;
        sm = 0;
        sy = 116;
        Date d = new Date();
        em = d.getMonth();
        ey = d.getYear();
        DateFormat df = new SimpleDateFormat("MM.yy");
        while (start) {
            d = new Date(sy, sm, 1);
            for (i = 0; i < list.size(); i++) {
                if (list.contains(df.format(d))) {
                    if (count)
                        k += countBookList(df.format(d));
                    else
                        downloadBookList(df.format(d));
                    break;
                }
            }
            if (sy == ey && sm == em)
                break;
            sm++;
            if (sm == 12) {
                sm = 0;
                sy++;
            }
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
                        Thread.sleep(1000);
                        publishProgress();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
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

    public void downloadStyle(boolean replaceStyle) throws Exception {
        final File fLight = lib.getFile(Const.LIGHT);
        final File fDark = getFile(Const.DARK);
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

    private int workWithList(File file, boolean count) throws Exception {
        downloadStyle(false);
        BufferedReader br = new BufferedReader(new FileReader(file));
        String s;
        int k = 0;
        while ((s = br.readLine()) != null && start) {
            if (s.contains(Const.LINK)) {
                if (count)
                    k++;
                else {
                    s = s.substring(Const.LINK.length());
                    downloadPage(s, false);
                    prog++;
                }
            }
        }
        br.close();
        return k;
    }

    public boolean downloadPage(String link, boolean singlePage) throws Exception {
        msg = link;
        // если singlePage=true, значит страницу страницу перезагружаем, а счетчики обрабатываем
        DataBase dataBase = new DataBase(context, link);
        if (!singlePage && dataBase.existsPage(link))
            return false;
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
                Cursor cursor = db.query(DataBase.TITLE, new String[]{DataBase.ID, DataBase.TITLE},
                        DataBase.LINK + DataBase.Q, new String[]{link}
                        , null, null, null);
                s = "";
                if (cursor.moveToFirst()) {
                    id = cursor.getInt(0);
                    s = cursor.getString(1);
                }
                cursor.close();
                cv = new ContentValues();
                cv.put(DataBase.TIME, System.currentTimeMillis());
                if (id == 0) { // id не найден, материала нет - добавляем
                    cv.put(DataBase.TITLE, getTitle(line, dataBase.getName()));
                    cv.put(DataBase.LINK, link);
                    id = (int) db.insert(DataBase.TITLE, null, cv);
                    //обновляем дату изменения списка:
                    cv = new ContentValues();
                    cv.put(DataBase.TIME, System.currentTimeMillis());
                    db.update(DataBase.TITLE, cv, DataBase.ID +
                            DataBase.Q, new String[]{"1"});
                } else { // id найден, значит материал есть
                    if (s.contains("/")) // в заголовке ссылка, необходимо заменить
                        cv.put(DataBase.TITLE, getTitle(line, dataBase.getName()));
                    //обновляем дату загрузки материала
                    db.update(DataBase.TITLE, cv, DataBase.ID +
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

    public void initClient() throws Exception {
        builderRequest = new Request.Builder();
        builderRequest.header(Const.USER_AGENT, context.getPackageName());
        builderRequest.header("Referer", Const.SITE);
        client = lib.createHttpClient();
    }
}
