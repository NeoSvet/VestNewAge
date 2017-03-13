package ru.neosvet.utils;

import android.app.Activity;
import android.app.ProgressDialog;
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
import java.util.Date;

import ru.neosvet.vestnewage.BrowserActivity;
import ru.neosvet.vestnewage.CalendarFragment;
import ru.neosvet.vestnewage.MainActivity;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.SiteFragment;
import ru.neosvet.vestnewage.SummaryFragment;

public class LoaderTask extends AsyncTask<String, Integer, Boolean> implements Serializable {
    private int MAX = 6;
    private transient Activity act;
    private transient Lib lib;
    private transient ProgressDialog di;
    private int prog = 0, sub_prog = 0;
    private String msg;
    private boolean boolStart = true;

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);
        if (values.length == 1) { //set max
            MAX = 2;
            di.dismiss();
            showD();
            return;
        }
        di.setProgress(prog);
        di.setMessage(msg + (sub_prog > 0 ? " (" + sub_prog + ")" : ""));
    }

    public void setAct(Activity act) {
        this.act = act;
        lib = new Lib(act);
        if (di != null && boolStart) {
            di.dismiss();
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
        di = new ProgressDialog(act);
        di.setTitle(act.getResources().getString(R.string.load));
        di.setMessage(msg);
        di.setMax(MAX);
        di.setProgress(prog);
        di.setIndeterminate(false);
        di.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        di.setProgressDrawable(act.getResources().getDrawable(R.drawable.progress_bar));
        di.setOnCancelListener(new ProgressDialog.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                boolStart = false;
            }
        });
        di.show();
    }

    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);
        if (act instanceof BrowserActivity) {
            ((BrowserActivity) act).finishLoad(result);
        } else {
            di.dismiss();
            if (boolStart) {
                ((MainActivity) act).finishAllLoad(result, MAX == 6);
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
                    publishProgress(2);
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
                downloadPage(link, true);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            prog = MAX;
        }
        return false;
    }

    private void refreshLists(int p) throws Exception {
        sub_prog = 0;
        if (p == -1 || p == R.id.nav_rss) {
            msg = act.getResources().getString(R.string.refresh_list)
                    + " " + act.getResources().getString(R.string.rss);
            publishProgress();
            SummaryTask t1 = new SummaryTask((MainActivity) act);
            t1.downloadList();
            prog++;
        }
        if (!boolStart) return;

        if (p == -1 || p == R.id.nav_main) {
            msg = act.getResources().getString(R.string.refresh_list)
                    + " " + act.getResources().getString(R.string.main);
            publishProgress();
            SiteTask t4 = new SiteTask((MainActivity) act);
            String[] url = new String[]{
                    Lib.SITE,
                    Lib.SITE + "novosti.html",
                    Lib.SITE + "media.html"
            };
            String[] file = new String[]{
                    getFile(SiteFragment.MAIN).toString(),
                    getFile(SiteFragment.NEWS).toString(),
                    getFile(SiteFragment.MEDIA).toString()
            };
            for (int i = 0; i < url.length && boolStart; i++) {
                t4.downloadList(url[i]);
                t4.saveList(file[i]);
            }
            prog++;
        }
        if (!boolStart) return;

        if (p == -1 || p == R.id.nav_calendar) {
            msg = act.getResources().getString(R.string.refresh_list)
                    + " " + act.getResources().getString(R.string.calendar);
            sub_prog = 0;
            publishProgress();
            Date d = new Date();
            CalendarTask t2 = new CalendarTask(act);
            int max_y = d.getYear() + 1, max_m = 12;
            for (int y = 116; y < max_y && boolStart; y++) {
                if (y == max_y - 1)
                    max_m = d.getMonth() + 1;
                for (int m = 0; m < max_m && boolStart; m++) {
                    t2.downloadCalendar(y, m);
                    sub_prog++;
                }
            }
            prog++;
        }
        if (!boolStart) return;

        if (p == -1 || p == R.id.nav_book) {
            msg = act.getResources().getString(R.string.refresh_list)
                    + " " + act.getResources().getString(R.string.book);
            sub_prog = 0;
            publishProgress();
            BookTask t3 = new BookTask((MainActivity) act);
            t3.downloadData(true);
            t3.downloadData(false);
            prog++;
        }
    }

    private void downloadAll(int p) throws Exception {
        if (!boolStart) return;
        File[] d;
        if (p == -1) { //download all
            d = new File[]{
                    getFile(Lib.LIST),
                    getFile(SiteFragment.MAIN),
                    getFile(SiteFragment.MEDIA)
            };
            msg = act.getResources().getString(R.string.download)
                    + " " + act.getResources().getString(R.string.kat_n_pos);
        } else { // download it
            switch (p) {
                case R.id.nav_rss:
                    d = new File[]{
                            getFile(SummaryFragment.RSS)
                    };
                    msg = act.getResources().getString(R.string.download)
                            + " " + act.getResources().getString(R.string.materials);
                    break;
                case R.id.nav_main:
                    d = new File[]{
                            getFile(SiteFragment.MAIN),
                            getFile(SiteFragment.MEDIA)
                    };
                    msg = act.getResources().getString(R.string.download)
                            + " " + act.getResources().getString(R.string.materials);
                    break;
                case R.id.nav_calendar:
                    d = new File[]{
                            getFile(CalendarFragment.CALENDAR)
                    };
                    msg = act.getResources().getString(R.string.download)
                            + " " + act.getResources().getString(R.string.kat_n_pos);
                    break;
                default:
                    d = new File[]{
                            getFile(Lib.LIST)
                    };
                    msg = act.getResources().getString(R.string.download)
                            + " " + act.getResources().getString(R.string.kat_n_pos);
                    break;
            }
        }
        publishProgress();
        for (int i = 0; i < d.length && boolStart; i++) {
            if (i == 2) {
                sub_prog = 0;
                prog++;
                msg = act.getResources().getString(R.string.download)
                        + " " + act.getResources().getString(R.string.materials);
                publishProgress();
            }
            if (d[i].isDirectory()) {
                File[] f = d[i].listFiles();
                for (int j = 0; j < f.length && boolStart; j++) {
                    if (f[j].isFile())
                        downloadList(f[j].toString());
                }
            } else
                downloadList(d[i].toString());
        }
        prog = MAX;
        publishProgress();
    }

    private void startTimer() {
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    while (boolStart && prog < MAX) {
                        Thread.sleep(1000);
                        publishProgress();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        t.start();
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
        final File fLight = lib.getFile(Lib.LIGHT);
        final File fDark = getFile(Lib.DARK);
        if (!fLight.exists() || !fDark.exists() || bReplaceStyle) {
            String line = "";
            int i;
            InputStream in = new BufferedInputStream(lib.getStream(Lib.SITE + "org/otk/tpl/otk/css/style-print.css"));
            BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
            BufferedWriter bwLight = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fLight)));
            BufferedWriter bwDark = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fDark)));
            for (i = 0; i < 7; i++) {
                br.readLine();
            }
            while ((line = br.readLine()) != null) {
                bwLight.write(line + Lib.N);
                if (line.contains("#000")) {
                    line = line.replace("000000", "000").replace("#000", "#fff");
                } else
                    line = line.replace("#fff", "#000");
                line = line.replace("333333", "333").replace("#333", "#ccc");
                bwDark.write(line + Lib.N);
                if (line.contains("body")) {
                    line = "    padding-left: 5px;\n    padding-right: 5px;";
                    bwLight.write(line + Lib.N);
                    bwDark.write(line + Lib.N);
                } else if (line.contains("print2")) {
                    line = br.readLine().replace("8pt/9pt", "12pt");
                    bwLight.write(line + Lib.N);
                    bwDark.write(line + Lib.N);
                }
                bwLight.flush();
                bwDark.flush();
            }
            br.close();
            bwLight.close();
            bwDark.close();
        }
    }

    private void downloadList(String path) throws Exception {
        downloadStyle(false);
        if (!boolStart) return;
        File f = new File(path);
        BufferedReader br = new BufferedReader(new FileReader(f));
        String s;
        while ((s = br.readLine()) != null && boolStart) {
            if (s.contains(Lib.LINK)) {
                s = s.substring(Lib.LINK.length());
                if (!s.contains(".html")) s += ".html";
                if (!lib.existsPage(s))
                    downloadPage(s, false);
                sub_prog++;
            }
        }
        br.close();
    }

    private void downloadPage(String link, boolean bCounter) throws Exception {
        String line, s = lib.getDatePage(link);
        InputStream in = new BufferedInputStream(lib.getStream(Lib.SITE + link + Lib.PRINT));
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        DataBase dbTable = new DataBase(act, s);
        SQLiteDatabase db = dbTable.getWritableDatabase();
        ContentValues cv;
        boolean b = false;
        int i = 0;
        while ((line = br.readLine()) != null) {
            if (b) {
                if (line.contains("<!--/row-->")) {
                    break;
                } else if (line.contains("<")) {
                    line = line.trim();
                    if (line.length() < 7) continue;
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
                    }
                    line = line.replace("color", "cvet");
                    cv = new ContentValues();
                    cv.put(DataBase.ID, i);
                    cv.put(DataBase.PARAGRAPH, line);
                    db.insert(DataBase.PARAGRAPH, null, cv);
                }
            } else if (line.contains("<h1")) {
                Cursor cursor = db.query(DataBase.TITLE, new String[]{DataBase.ID},
                        DataBase.LINK + DataBase.Q, new String[]{link}
                        , null, null, null);
                if (cursor.moveToFirst())
                    i = cursor.getInt(0);
                if (i == 0) { // not exists title - add
                    line = line.trim().replace("&nbsp;", " ");
                    while ((i = line.indexOf("<")) > -1) {
                        line = line.substring(0, i) +
                                line.substring(line.indexOf(">", i) + 1);
                    }
                    cv = new ContentValues();
                    cv.put(DataBase.LINK, link);
                    cv.put(DataBase.TIME, System.currentTimeMillis());
                    if (line.contains(s)) {
                        line = line.substring(9);
                        if (line.contains(Lib.KV_OPEN))
                            line = line.substring(line.indexOf(Lib.KV_OPEN) + 1, line.length() - 1);
                    }
                    cv.put(DataBase.TITLE, line);
                    i = (int) db.insert(DataBase.TITLE, null, cv);
                    //обновляем дату изменения списка:
                    cv = new ContentValues();
                    cv.put(DataBase.TIME, System.currentTimeMillis());
                    db.update(DataBase.TITLE, cv,
                            DataBase.ID + DataBase.Q, new String[]{"1"});
                }
                cursor.close();
                br.readLine();
                b = true;
            } else if (line.contains("counter") && bCounter) { // counters
                sendCounter(line);
            }
        }
        br.close();
        if (bCounter)
            checkNoreadList(link);
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

    private void checkNoreadList(String link) {
        try {
            File file = new File(act.getFilesDir() + File.separator + Lib.NOREAD);
            if (file.exists()) {
                boolean b = false;
                String t, l;
                final String N = "\n";
                StringBuilder f = new StringBuilder();
                BufferedReader br = new BufferedReader(new InputStreamReader(act.openFileInput(file.getName())));
                while ((t = br.readLine()) != null) {
                    l = br.readLine();
                    if (l.contains(link)) {
                        b = true;
                    } else {
                        f.append(t);
                        f.append(N);
                        f.append(l);
                        f.append(N);
                    }
                }
                br.close();
                if (b) {
                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(act.openFileOutput(Lib.NOREAD, act.MODE_PRIVATE)));
                    bw.write(f.toString());
                    bw.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
