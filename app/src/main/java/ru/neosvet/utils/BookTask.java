package ru.neosvet.utils;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import ru.neosvet.vestnewage.BookFragment;
import ru.neosvet.vestnewage.MainActivity;
import ru.neosvet.vestnewage.R;

public class BookTask extends AsyncTask<Integer, Boolean, String> implements Serializable {
    private final String SITE = "http://www.otkroveniya.eu/";
    private transient BookFragment frm;
    private transient MainActivity act;
    private List<String> title = new ArrayList<String>();
    private List<String> links = new ArrayList<String>();
    private transient ProgressDialog di;
    private int prog = 0;
    private String msg = null;
    private boolean boolStart = true;

    public BookTask(BookFragment frm) {
        setFrm(frm);
    }

    public BookTask(MainActivity act) {
        this.act = act;
    }

    public void setFrm(BookFragment frm) {
        this.frm = frm;
        act = (MainActivity) frm.getActivity();
        if (msg != null)
            publishProgress(true);
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        if (di != null)
            di.dismiss();
        if (frm != null) {
            frm.finishLoad(result);
        }
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected void onProgressUpdate(Boolean... values) {
        super.onProgressUpdate(values);
        if (values[0]) {
            di = new ProgressDialog(act);
            di.setTitle(act.getResources().getString(R.string.load));
            di.setMessage(msg);
            di.setMax(1010);
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
        } else {
            di.setMessage(msg);
            di.setProgress(prog);
        }
    }

    @Override
    protected String doInBackground(Integer... params) {
        try {
            if (params[0] == 3)
                return downloadOtrk();
            return downloadData(params[0] == 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private String downloadOtrk() throws Exception {
        msg = act.getResources().getString(R.string.start);
        publishProgress(true);
        String[] urls = {"/print/otkroveniya.html", "/print/tolkovaniya.html"};
        InputStream in;
        BufferedReader br;
        boolean b;
        int n;
        String line, t, s, date1 = "", date2;
        for (int i = 0; i < urls.length && boolStart; i++) {
            in = new BufferedInputStream(act.lib.getStream(SITE + urls[i]));
            br = new BufferedReader(new InputStreamReader(in, "cp1251"), 1000);
            b = false;
            while ((line = br.readLine()) != null && boolStart) {
                if (!b)
                    b = line.contains("h2");//razdel
                else if (line.contains("years") || line.contains("2016"))
                    break;
                else if (line.contains(Lib.HREF)) {
                    n = line.indexOf(Lib.HREF) + 7;
                    s = line.substring(n, line.indexOf("\"", n));
                    if (line.contains("<nobr>"))
                        t = act.lib.withOutTags(line);
                    else
                        t = line.substring(line.indexOf(">", n) + 1, line.indexOf("<", n));
                    msg = t;
                    publishProgress(false);
                    date2 = DataBase.getDatePage(s);
                    if (!date2.equals(date1)) {
                        date1 = date2;
                        addOtrkPage(t, s, date1, true);
                    } else
                        addOtrkPage(t, s, date1, false);
                    prog++;
                }
            }
            br.close();
        }
        return date1 + (boolStart ? 1 : 0);
    }

    private void addOtrkPage(String title, String link, String date, boolean boolFirst) throws Exception {
        if (link.contains("#2")) return;
        DataBase dataBase = new DataBase(act, date);
        SQLiteDatabase db = dataBase.getWritableDatabase();
        ContentValues cv = new ContentValues();
        // если эта страница первая за этот месяце, то добавляем дату обновления списка:
        if (boolFirst) {
            cv.put(DataBase.TIME, System.currentTimeMillis());
            if (db.update(DataBase.TITLE, cv,
                    DataBase.TITLE + DataBase.Q, new String[]{title}) == 1) {
                // обновить получилось, значит надо базу очистить
                db.delete(DataBase.TITLE, null, null);
                db.delete(DataBase.PARAGRAPH, null, null);
            }
            db.insert(DataBase.TITLE, null, cv);
            cv = new ContentValues();
        }
        // добавление страницы в список:
        cv.put(DataBase.TITLE, title);
        cv.put(DataBase.LINK, link);
        cv.put(DataBase.TIME, System.currentTimeMillis());
        int id = (int) db.insert(DataBase.TITLE, null, cv);
        // загрузка страницы:
        InputStream in = new BufferedInputStream(act.lib.getStream(SITE + link));
        BufferedReader br = new BufferedReader(new InputStreamReader(in, "cp1251"), 1000);
        boolean b = false;
        String line;
        while ((line = br.readLine()) != null) {
            if (line.contains("<h1>")) {
                if (line.contains("name=")) { //<div class="next"><a name="2"><h1>
                    if (link.contains("24.01.07") || !link.contains("#")) {//дополнение || print/2010/15.01.10.html
                        line = "<h1>" + act.lib.withOutTags(line) + "</h1>";
                        cv = new ContentValues();
                        cv.put(DataBase.ID, id);
                        cv.put(DataBase.PARAGRAPH, line);
                        db.insert(DataBase.PARAGRAPH, null, cv);
                    } else {
                        cv = new ContentValues();
                        line = act.lib.withOutTags(line);
                        if (line.contains(date))
                            line = line.substring(line.indexOf(" ") + 1);
                        cv.put(DataBase.TITLE, line);
                        cv.put(DataBase.LINK, link.replace("#1", "#2"));
                        cv.put(DataBase.TIME, System.currentTimeMillis());
                        id = (int) db.insert(DataBase.TITLE, null, cv);
                    }
                } else
                    b = true;
            } else if (b) {
                if (line.contains("print2"))
                    break;
                line = line.replace("color", "cvet");
                if (line.contains("</td>"))
                    line = line.substring(0, line.length() - 10);
                cv = new ContentValues();
                cv.put(DataBase.ID, id);
                cv.put(DataBase.PARAGRAPH, line);
                db.insert(DataBase.PARAGRAPH, null, cv);
            }
        }
        br.close();
        dataBase.close();
    }

    public String downloadData(boolean boolKat) throws Exception {
        String url = Lib.SITE + (boolKat ? Lib.POEMS : "tolkovaniya") + Lib.PRINT;
        InputStream in = new BufferedInputStream(act.lib.getStream(url));
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        boolean b = false;
        int i, n;
        String line, t, s, date1 = "", date2;
        while ((line = br.readLine()) != null) {
            if (!b)
                b = line.contains("h2");//razdel
            else if (line.contains("clear"))
                break;
            else if (line.contains(Lib.HREF)) {
                if (line.contains("years"))
                    line = line.substring(0, line.indexOf("years"));
                n = 0;
                while (line.indexOf(Lib.HREF, n) > -1) {
                    n = line.indexOf(Lib.HREF, n) + 7;
                    s = line.substring(n, line.indexOf("'", n)); //)-5
                    i = s.indexOf(".") + 1;
                    date2 = s.substring(i, i + 5);
                    if (!date2.equals(date1)) {
                        saveData(date1);
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
        return date1;
    }

    private void saveData(String date) throws Exception {
        if (title.size() > 0) {
            DataBase dataBase = new DataBase(act, date);
            SQLiteDatabase db = dataBase.getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put(DataBase.TIME, System.currentTimeMillis());
            if (db.update(DataBase.TITLE, cv,
                    DataBase.ID + DataBase.Q, new String[]{"1"}) == 0) {
                db.insert(DataBase.TITLE, null, cv);
            }
            for (int i = 0; i < title.size(); i++) {
                cv = new ContentValues();
                cv.put(DataBase.TITLE, title.get(i));
                // пытаемся обновить запись:
                if (db.update(DataBase.TITLE, cv,
                        DataBase.LINK + DataBase.Q,
                        new String[]{links.get(i)}) == 0) {
                    // обновить не получилось, добавляем:
                    cv.put(DataBase.LINK, links.get(i));
                    db.insert(DataBase.TITLE, null, cv);
                }
            }
            dataBase.close();
            title.clear();
            links.clear();
        }
    }
}
