package ru.neosvet.utils;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.Serializable;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import ru.neosvet.blagayavest.BrowserActivity;
import ru.neosvet.blagayavest.MainActivity;
import ru.neosvet.blagayavest.R;
import ru.neosvet.ui.MyActivity;

/**
 * Created by NeoSvet on 16.12.2016.
 */

public class LoaderTask extends AsyncTask<String, Integer, Boolean> implements Serializable {
    private transient AppCompatActivity act;
    private transient Lib lib;
    private transient ProgressDialog di;
    private int prog = 0, sub_prog = 0;
    private String msg;
    private boolean boolStart = true;

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);
//        prog = values[0];
        di.setProgress(prog);
        di.setMessage(msg + (sub_prog > 0 ? " (" + sub_prog + ")" : ""));
    }

    public void setAct(AppCompatActivity act) {
        this.act = act;
        lib = new Lib(act);
        if (di != null && boolStart) {
            di.dismiss();
            showD();
        }
    }

    public LoaderTask(AppCompatActivity act) {
        this.act = act;
        lib = new Lib(act);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        if (act instanceof MyActivity) {
            msg = act.getResources().getString(R.string.start);
            showD();
        }
    }

    private void showD() {
        di = new ProgressDialog(act);
        di.setTitle(act.getResources().getString(R.string.load));
        di.setMessage(msg);
        di.setMax(5);
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
                ((MyActivity) act).finishAllLoad(result);
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
            if (params.length == 0) {
                startTimer();
                refreshLists();
                downloadAll();
                return true;
            }
            String p0 = params[0], p1 = params[1];
            if (p0.contains(".png"))
                lib.downloadFile(p0, p1);
            else {
                lib.downloadStyle(params.length == 3);
                lib.downloadPage(p0, p1, true);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            prog = 5;
        }
        return false;
    }

    private void refreshLists() throws Exception {
        msg = act.getResources().getString(R.string.refresh_list)
                + " " + act.getResources().getString(R.string.calendar);
        publishProgress();
        Date d = new Date();
        MyActivity mact = (MyActivity) act;
        CalendarTask t1 = new CalendarTask(mact);
        int max_y = d.getYear() + 1, max_m = 12;
        sub_prog = 0;
        for (int y = 116; y < max_y && boolStart; y++) {
            if (y == max_y)
                max_m = d.getMonth() + 1;
            for (int m = 0; m < max_m && boolStart; m++) {
//                Lib.LOG("calendar=" + m + "." + y);
                t1.downloadCalendar(y, m);
                sub_prog++;
            }
        }
        if (!boolStart) return;

        prog++;
        sub_prog = 0;
        msg = act.getResources().getString(R.string.refresh_list)
                + " " + act.getResources().getString(R.string.book);
        publishProgress();
        BookTask t2 = new BookTask(mact);
//        Lib.LOG("kat");
        t2.downloadData(true);
        if (!boolStart) return;
//        Lib.LOG("pos");
        t2.downloadData(false);

        prog++;
        msg = act.getResources().getString(R.string.refresh_list)
                + " " + act.getResources().getString(R.string.main);
        publishProgress();
        MainTask t3 = new MainTask(mact);
        String[] url = new String[]{
                Lib.SITE,
                Lib.SITE + "novosti.html",
                Lib.SITE + "media.html"
        };
        String[] file = new String[]{
                getFile(MainActivity.MAIN).toString(),
                getFile(MainActivity.NEWS).toString(),
                getFile(MainActivity.MEDIA).toString()
        };
        for (int i = 0; i < url.length && boolStart; i++) {
//            Lib.LOG(url[i]);
            t3.downloadList(url[i]);
            t3.saveList(file[i]);
        }
    }

    private void downloadAll() throws Exception {
        if (!boolStart) return;
        File[] d = new File[]{
//                getFile(CalendarActivity.CALENDAR),
                getFile(Lib.LIST),
                getFile(MainActivity.MAIN),
                getFile(MainActivity.MEDIA)
        };
        prog++;
        msg = act.getResources().getString(R.string.download)
                + " " + act.getResources().getString(R.string.kat_n_pos);
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
//            Lib.LOG("sub_prog="+sub_prog);
        }
        prog = 5;
        publishProgress();
    }

    private void startTimer() {
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    while (boolStart && prog < 5) {
                        TimeUnit.SECONDS.sleep(1);
                        publishProgress();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        t.start();
    }

    public void downloadList(String path) throws Exception {
//        Lib.LOG("list="+path);
        lib.downloadStyle(false);
        if (!boolStart) return;
        File f = new File(path);//context.getFilesDir() +
        BufferedReader br = new BufferedReader(new FileReader(f));
        String s;
        while ((s = br.readLine()) != null && boolStart) {
            if (s.contains(Lib.LINK)) {
                s = s.substring(Lib.LINK.length());
//                Lib.LOG("link=" + s);
                if (s.contains("/"))
                    f = lib.getPageFile(s);
                else {
                    f = getFile("/" + BrowserActivity.ARTICLE);
                    if (!f.exists())
                        f.mkdir();
                    f = getFile("/" + BrowserActivity.ARTICLE + "/" + s);
                }
//                Lib.LOG("path=" + f.toString());
                if (!f.exists()) {
//                    Lib.LOG("download");
                    lib.downloadPage(s + Lib.print, f.toString(), false);
                    sub_prog++;
                }
            }
        }
        br.close();
    }
}
