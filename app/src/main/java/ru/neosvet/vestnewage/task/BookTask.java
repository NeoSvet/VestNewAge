package ru.neosvet.vestnewage.task;

import android.content.ContentValues;
import android.content.DialogInterface;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.support.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import ru.neosvet.ui.dialogs.ProgressDialog;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.fragment.BookFragment;

public class BookTask extends AsyncTask<Integer, Boolean, String> implements Serializable {
    private transient BookFragment frm;
    private transient MainActivity act;
    private List<String> title = new ArrayList<String>();
    private List<String> links = new ArrayList<String>();
    private transient ProgressDialog dialog;
    private int prog = 0;
    private String msg = null;
    private boolean start = true;

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
        if (dialog != null)
            dialog.dismiss();
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
            dialog = new ProgressDialog(act, 137);
            dialog.setOnCancelListener(new ProgressDialog.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    start = false;
                }
            });
            dialog.show();
            dialog.setMessage(msg);
            dialog.setProgress(prog);
        } else {
            dialog.setMessage(msg);
            dialog.setProgress(prog);
        }
    }

    @Override
    protected String doInBackground(Integer... params) {
        try {
            if (params[0] == 3)
                return downloadOtrk(true);
            if (params[0] == 1 && params[1] == 1) //если вкладка Послания и Откровения были загружены, то их тоже надо обновить
                downloadOtrk(false);
            return downloadData(params[0] == 0, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private String downloadOtrk(boolean withDialog) throws Exception {
        if (withDialog) {
            msg = act.getResources().getString(R.string.start);
            publishProgress(true);
        }
        final String path = act.lib.getDBFolder() + "/";
        File f;
        String s;
        long l;
        BufferedInputStream in = new BufferedInputStream(act.lib.getStream("http://neosvet.ucoz.ru/databases_vna/list.txt"));
        //list format:
        //01.05 delete [time] - при необходимости список обновить
        //02.05 [length] - проверка целостности
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        while ((s = br.readLine()) != null) {
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
        int m = 8, y = 4;
        String name = "01.16";
        DataBase dataBase;
        SQLiteDatabase db;
        ContentValues cv;
        boolean isTitle;
        final long time = System.currentTimeMillis();
        while (y < 16 && start) {
            name = (m < 10 ? "0" : "") + m + "." + (y < 10 ? "0" : "") + y;
            if (withDialog) {
                msg = act.getResources().getStringArray(R.array.months)[m - 1] + " " + (2000 + y);
                publishProgress(false);
            }
            f = new File(path + name);
            if (!f.exists()) {
                dataBase = new DataBase(act, name);
                db = dataBase.getWritableDatabase();
                isTitle = true;
                in = new BufferedInputStream(act.lib.getStream("http://neosvet.ucoz.ru/databases_vna/" + name));
                br = new BufferedReader(new InputStreamReader(in, "cp1251"), 1000);
                while ((s = br.readLine()) != null) {
                    if (s.equals(Const.AND)) {
                        isTitle = false;
                        s = br.readLine();
                    }
                    cv = new ContentValues();
                    if (isTitle) {
                        cv.put(DataBase.LINK, s);
                        cv.put(DataBase.TITLE, br.readLine());
                        cv.put(DataBase.TIME, time);
                        db.insert(DataBase.TITLE, null, cv);
                    } else {
                        cv.put(DataBase.ID, Integer.parseInt(s));
                        cv.put(DataBase.PARAGRAPH, br.readLine());
                        db.insert(DataBase.PARAGRAPH, null, cv);
                    }
                }
                br.close();
                dataBase.close();
            }
            if (m == 12) {
                m = 1;
                y++;
            } else
                m++;
            prog++;
        }
        return name + (start ? 1 : 0);
    }

    public String downloadData(boolean katren, @Nullable LoaderTask loader) throws Exception {
        String url = Const.SITE + (katren ? Const.POEMS : "tolkovaniya") + Const.PRINT;
        InputStream in = new BufferedInputStream(act.lib.getStream(url));
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        boolean begin = false;
        int i, n;
        String line, t, s, date1 = "", date2;
        while ((line = br.readLine()) != null) {
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
                        if (loader != null)
                            loader.upProg();
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
