package ru.neosvet.vestnewage.task;

import android.app.Activity;
import android.content.ContentValues;
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
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ru.neosvet.ui.ProgressDialog;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.BrowserActivity;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.fragment.CalendarFragment;
import ru.neosvet.vestnewage.fragment.SiteFragment;
import ru.neosvet.vestnewage.fragment.SummaryFragment;

public class LoaderTask extends AsyncTask<String, Integer, Boolean> implements Serializable {
    private int max = 1;
    private transient Activity act;
    private transient Lib lib;
    private transient ProgressDialog di;
    private int prog = 0;
    private String msg;
    private boolean boolStart = true, boolAll = true;

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);
        if (values.length == 1) { //set max
            max = values[0];
            showD();
        } else {
            di.setProgress(prog);
            di.setMessage(msg);
        }
    }

    public void setAct(Activity act) {
        this.act = act;
        lib = new Lib(act);
        if (boolStart) {
            showD();
        }
    }

    public LoaderTask(Activity act) {
        this.act = act;
        lib = new Lib(act);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        if (act instanceof MainActivity) {
            msg = act.getResources().getString(R.string.start);
            showD();
        }
    }

    private void showD() {
        if (di != null)
            di.dismiss();
        di = new ProgressDialog(act, max);
        di.setOnCancelListener(new ProgressDialog.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                boolStart = false;
            }
        });
        di.show();
        di.setMessage(msg);
        di.setProgress(prog);
    }

    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);
        if (act instanceof BrowserActivity) {
            ((BrowserActivity) act).finishLoad(result);
        } else {
            di.dismiss();
            if (boolStart) {
                ((MainActivity) act).finishAllLoad(result, boolAll);
                boolStart = false;
            }
        }
    }

    private File getFile(String name) {
        return new File(act.getFilesDir() + name);
    }

    @Override
    protected Boolean doInBackground(String... params) {
        try {
            if (params.length < 2) {
                startTimer();
                int p = -1;
                if (params.length == 1) {
                    p = Integer.parseInt(params[0]);
                    boolAll = false;
                }
                refreshLists(p);
                downloadAll(p);
                return true;
            }
            String link = params[0];
            if (link.contains(".png"))
                downloadFile(link, params[1]);
            else {
                downloadStyle(params.length == 3);
                if (!link.equals(""))
                    downloadPage(link, true);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void refreshLists(int p) throws Exception {
        msg = act.getResources().getString(R.string.download_list);
        // подсчёт количества списков:
        int k = 0;
        if (p == -1 || p == R.id.nav_book) k = 1;
        if (p == -1 || p == R.id.nav_calendar) {
            Date d = new Date();
            k += (d.getYear() - 116) * 12 + d.getMonth() + 1;
            if (p == -1) k += 4;
        } else if (p == R.id.nav_main)
            k = 3;
        else if (p == R.id.nav_rss)
            k = 1;
        publishProgress(k);
        //загрузка списков
        if (p == -1 || p == R.id.nav_rss) {
            SummaryTask t1 = new SummaryTask((MainActivity) act);
            t1.downloadList();
            prog++;
        }
        if (!boolStart) return;

        if (p == -1 || p == R.id.nav_main) {
            SiteTask t4 = new SiteTask((MainActivity) act);
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
            for (int i = 0; i < url.length && boolStart; i++) {
                t4.downloadList(url[i]);
                t4.saveList(file[i]);
                prog++;
            }
        }
        if (!boolStart) return;

        if (p == -1 || p == R.id.nav_calendar) {
            Date d = new Date();
            CalendarTask t2 = new CalendarTask(act);
            int max_y = d.getYear() + 1, max_m = 12;
            for (int y = 116; y < max_y && boolStart; y++) {
                if (y == max_y - 1)
                    max_m = d.getMonth() + 1;
                for (int m = 0; m < max_m && boolStart; m++) {
                    t2.downloadCalendar(y, m, false);
                    prog++;
                }
            }
        }
        if (!boolStart) return;

        if (p == -1 || p == R.id.nav_book) {
            BookTask t3 = new BookTask((MainActivity) act);
            t3.downloadData(true);
            t3.downloadData(false);
            prog++;
        }
    }

    private void downloadAll(int p) throws Exception {
        prog = 0;
        msg = act.getResources().getString(R.string.download_materials);
        if (!boolStart) return;
        File[] d;
        if (p == -1) { //download all
            d = new File[]{
                    null,
                    getFile(SiteFragment.MAIN),
                    getFile(SiteFragment.MEDIA)
            };
        } else { // download it
            switch (p) {
                case R.id.nav_rss:
                    d = new File[]{getFile(SummaryFragment.RSS)};
                    break;
                case R.id.nav_main:
                    d = new File[]{
                            getFile(SiteFragment.MAIN),
                            getFile(SiteFragment.MEDIA)
                    };
                    break;
                case R.id.nav_calendar:
                    d = getFile(CalendarFragment.FOLDER).listFiles();
                    break;
                default: //R.id.nav_book:
                    d = new File[]{null};
                    break;
            }
        }
        // подсчёт количества страниц:
        int k = 0;
        for (int i = 0; i < d.length && boolStart; i++) {
            if (d[i] == null)
                k += workWithBook(true);
            else
                k += workWithList(d[i], true);
        }
        publishProgress(k);
        // загрузка страниц:
        for (int i = 0; i < d.length && boolStart; i++) {
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
        for (i = 0; i < f.length && boolStart; i++) {
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
        while (boolStart) {
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
        DataBase dataBase = new DataBase(act, name);
        SQLiteDatabase db = dataBase.getWritableDatabase();
        Cursor curTitle = db.query(DataBase.TITLE, null, null, null, null, null, null);
        int k = curTitle.getCount();
        curTitle.close();
        dataBase.close();
        return k;
    }

    private void downloadBookList(String name) throws Exception {
        DataBase dataBase = new DataBase(act, name);
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
                    while (boolStart && prog < max) {
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
            OutputStream out = new FileOutputStream(f, false);
            byte[] buf = new byte[1024];
            InputStream in = new BufferedInputStream(lib.getStream(url));
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

    private void downloadStyle(boolean bReplaceStyle) throws Exception {
        final File fLight = lib.getFile(Const.LIGHT);
        final File fDark = getFile(Const.DARK);
        if (!fLight.exists() || !fDark.exists() || bReplaceStyle) {
            String line = "";
            int i;
            InputStream in = new BufferedInputStream(lib.getStream(Const.SITE + "org/otk/tpl/otk/css/style-print.css"));
            BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
            BufferedWriter bwLight = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fLight)));
            BufferedWriter bwDark = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fDark)));
            for (i = 0; i < 7; i++) {
                br.readLine();
            }
            while ((line = br.readLine()) != null) {
                bwLight.write(line + Const.N);
                if (line.contains("#000")) {
                    line = line.replace("000000", "000").replace("#000", "#fff");
                } else
                    line = line.replace("#fff", "#000");
                if (line.contains("P B {")) { //correct bold
                    br.readLine(); //font-weight:600;
                    br.readLine(); //}
                    line = br.readLine();
                }
                line = line.replace("333333", "333").replace("#333", "#ccc");
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
        while ((s = br.readLine()) != null && boolStart) {
            if (s.contains(Const.LINK)) {
                if (count)
                    k++;
                else {
                    s = s.substring(Const.LINK.length());
                    if (!s.contains(Const.HTML)) {
                        if (s.contains("#"))
                            s = s.substring(0, s.indexOf("#")) +
                                    Const.HTML + s.substring(s.indexOf("#"));
                        else
                            s += Const.HTML;
                    }
                    downloadPage(s, false);
                    prog++;
                }
            }
        }
        br.close();
        return k;
    }

    private void downloadPage(String link, boolean bSinglePage) throws Exception {
        msg = link;
        // если bSinglePage=true, значит страницу страницу перезагружаем, а счетчики обрабатываем
        DataBase dataBase = new DataBase(act, link);
        if (!bSinglePage && dataBase.existsPage(link))
            return;
        String line, s = link;
        final String par = "</p>";
        if (link.contains("#")) s = s.substring(0, s.indexOf("#"));
        InputStream in = new BufferedInputStream(lib.getStream(Const.SITE + s + Const.PRINT));
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        SQLiteDatabase db = dataBase.getWritableDatabase();
        ContentValues cv;
        boolean b = false;
        int id = 0;
        while ((line = br.readLine()) != null) {
            if (b) {
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
                        s = "<a href=\"https://vimeo.com/" +
                                s + "\">" +
                                act.getResources().getString
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
                Cursor cursor = db.query(DataBase.TITLE, new String[]{DataBase.ID},
                        DataBase.LINK + DataBase.Q, new String[]{link}
                        , null, null, null);
                if (cursor.moveToFirst())
                    id = cursor.getInt(0);
                cv = new ContentValues();
                cv.put(DataBase.TIME, System.currentTimeMillis());
                if (id == 0) { // id не найден, материала нет - добавляем
                    line = line.replace("&ldquo;", "“").replace("&rdquo;", "”");
                    line = Lib.withOutTags(line);

                    cv.put(DataBase.LINK, link);
                    if (line.contains(dataBase.getName())) {
                        line = line.substring(9);
                        if (line.contains(Const.KV_OPEN))
                            line = line.substring(line.indexOf(Const.KV_OPEN) + 1, line.length() - 1);
                    }
                    cv.put(DataBase.TITLE, line);
                    id = (int) db.insert(DataBase.TITLE, null, cv);
                    //обновляем дату изменения списка:
                    cv = new ContentValues();
                    cv.put(DataBase.TIME, System.currentTimeMillis());
                    db.update(DataBase.TITLE, cv, DataBase.ID +
                            DataBase.Q, new String[]{"1"});
                } else { // id найден, значит материал есть
                    //обновляем дату загрузки материала
                    db.update(DataBase.TITLE, cv, DataBase.ID +
                            DataBase.Q, new String[]{String.valueOf(id)});
                    //удаляем содержимое материала
                    db.delete(DataBase.PARAGRAPH, DataBase.ID +
                            DataBase.Q, new String[]{String.valueOf(id)});
                }
                cursor.close();
                br.readLine();
                b = true;
            } else if (line.contains("counter") && bSinglePage) { // счетчики
                sendCounter(line);
            }
        }
        br.close();
        dataBase.close();
    }

    private void sendCounter(String line) {
        int i = 0;
        while ((i = line.indexOf("img src", i)) > -1) {
            i += 9;
            final String link_counter = line.substring(i, line.indexOf("\"", i));
            new Thread(new Runnable() {
                public void run() {
                    try {
                        lib.getStream(link_counter);
                    } catch (Exception ex) {
                    }
                }
            }).start();
        }
    }
}
