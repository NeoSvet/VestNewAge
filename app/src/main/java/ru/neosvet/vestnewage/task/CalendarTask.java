package ru.neosvet.vestnewage.task;

import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ru.neosvet.ui.ListItem;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.Noread;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.activity.SlashActivity;
import ru.neosvet.vestnewage.fragment.CalendarFragment;

public class CalendarTask extends AsyncTask<Integer, Integer, Boolean> implements Serializable {
    private transient CalendarFragment frm;
    private transient Activity act;
    private transient DataBase dataBase;
    private transient SQLiteDatabase db;
    private transient Lib lib;
    private boolean loadList;
    private List<ListItem> data = new ArrayList<ListItem>();

    public CalendarTask(CalendarFragment frm) {
        setFrm(frm);
    }

    public CalendarTask(Activity act) {
        this.act = act;
        lib = new Lib(act);
    }

    public void setFrm(CalendarFragment frm) {
        this.frm = frm;
        act = frm.getActivity();
        lib = new Lib(act);
    }

    public void setAct(Activity act) {
        this.act = act;
        lib = new Lib(act);
    }

    public boolean isLoadList() {
        return loadList;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);
        if (frm != null) {
            int n = values[0];
            if (n == 0)
                frm.updateCalendar();
            else
                frm.blinkDay(n);
        }
    }

    @Override
    protected void onCancelled(Boolean result) {
        super.onCancelled(result);
        if (frm != null)
            frm.finishLoad(result);
    }

    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);
        if (frm != null) {
            frm.finishLoad(result);
        } else if (act != null) {
            if (act instanceof SlashActivity)
                ((SlashActivity) act).finishLoad();
        }
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected Boolean doInBackground(Integer... params) {
        try {
            loadList = true;
            downloadCalendar(params[0], params[1], params[2] == 1);
            if (isCancelled())
                return true;
            downloadAds();
            if (isCancelled())
                return true;
            loadList = false;
            publishProgress(0);
            downloadMonth(params[0], params[1]);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void downloadMonth(int year, int month) throws Exception {
        DateFormat df = new SimpleDateFormat("MM.yy");
        Date d = new Date(year, month, 1);
        DataBase dataBase = new DataBase(act, df.format(d));
        SQLiteDatabase db = dataBase.getWritableDatabase();
        Cursor curTitle = db.query(DataBase.TITLE, new String[]{DataBase.LINK},
                null, null, null, null, null);
        if (curTitle.moveToFirst()) {
            // пропускаем первую запись - там только дата изменения списка
            LoaderTask loader = new LoaderTask(act);
            loader.initClient();
            loader.downloadStyle(false);
            int n;
            String link;
            while (curTitle.moveToNext()) {
                link = curTitle.getString(0);
                if (loader.downloadPage(link, false)) {
                    n = link.lastIndexOf("/") + 1;
                    n = Integer.parseInt(link.substring(n, n + 2));
                    publishProgress(n);
                }
                if (isCancelled()) {
                    curTitle.close();
                    dataBase.close();
                    return;
                }
            }
        }
        curTitle.close();
        dataBase.close();
    }

    private void downloadAds() {
        try {
            String t = "0";
            BufferedReader br;
            File file = new File(act.getFilesDir() + File.separator + CalendarFragment.ADS);
            if (file.exists()) {
                br = new BufferedReader(new FileReader(file));
                t = br.readLine();
                br.close();
            }
            String s = "http://neosvet.ucoz.ru/ads_vna.txt";
            if (act instanceof MainActivity)
                br = new BufferedReader(new InputStreamReader(lib.getStream(s)));
            else
                br = new BufferedReader(new InputStreamReader(lib.getStream(s)));
            s = br.readLine();
            if (Long.parseLong(s) > Long.parseLong(t)) {
                BufferedWriter bw = new BufferedWriter(new FileWriter(file));
                bw.write(System.currentTimeMillis() + Const.N);
                while ((s = br.readLine()) != null) {
                    bw.write(s + Const.N);
                    bw.flush();
                }
                bw.close();
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void downloadCalendar(int year, int month, boolean boolNoread) throws Exception {
        try {
            InputStream in = new BufferedInputStream(lib.getStream(Const.SITE + "?json&year="
                    + (year + 1900) + "&month=" + (month + 1)));
            BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
            String s = br.readLine();
            br.close();
            if (isCancelled())
                return;

            JSONObject json, jsonI;
            JSONArray jsonA;
            String link;
            json = new JSONObject(s);
            int n;
            for (int i = 0; i < json.names().length(); i++) {
                s = json.names().get(i).toString();
                jsonI = json.optJSONObject(s);
                n = data.size();
                data.add(new ListItem(s.substring(s.lastIndexOf("-") + 1)));
                if (jsonI == null) { // несколько материалов за день
                    jsonA = json.optJSONArray(s);
                    for (int j = 0; j < jsonA.length(); j++) {
                        jsonI = jsonA.getJSONObject(j);
                        link = jsonI.getString(DataBase.LINK) + Const.HTML;
                        addLink(n, link);
                    }
                } else { // один материал за день
                    link = jsonI.getString(DataBase.LINK) + Const.HTML;
                    addLink(n, link);
                    jsonI = jsonI.getJSONObject("data");
                    if (jsonI != null) {
                        if (jsonI.has("title2")) {
                            if (!jsonI.getString("title2").equals(""))
                                addLink(n, link + "#2");
                        }
                    }
                }
            }
            dataBase.close();
            if (isCancelled()) {
                data.clear();
                return;
            }

            if (boolNoread) {
                Date dItem = new Date((month < 9 ? "0" : "") + (month + 1) + "/01/" + (year + 1900));
                Noread noread = new Noread(act);
                for (int i = 0; i < data.size(); i++) {
                    for (int j = 0; j < data.get(i).getCount(); j++) {
                        dItem.setDate(Integer.parseInt(data.get(i).getTitle()));
                        noread.addLink(data.get(i).getLink(j), dItem);
                    }
                }
                noread.close();
            }
            data.clear();
        } catch (org.json.JSONException e) {
        }
    }

    private void initDatebase(String link) {
        if (dataBase != null) return;
        dataBase = new DataBase(act, link);
        db = dataBase.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(DataBase.TIME, System.currentTimeMillis());
        if (db.update(DataBase.TITLE, cv,
                DataBase.ID + DataBase.Q, new String[]{"1"}) == 0) {
            db.insert(DataBase.TITLE, null, cv);
        }
    }

    private void addLink(int n, String link) {
        if (data.get(n).getCount() > 0) {
            String s = link.substring(link.lastIndexOf("/"));
            for (int i = 0; i < data.get(n).getCount(); i++) {
                if (data.get(n).getLink(i).contains(s))
                    return;
            }
        }
        data.get(n).addLink(link);
        initDatebase(link);
        ContentValues cv = new ContentValues();
        cv.put(DataBase.LINK, link);
        // пытаемся обновить запись:
        if (db.update(DataBase.TITLE, cv,
                DataBase.LINK + DataBase.Q,
                new String[]{link}) == 0) {
            // обновить не получилось, добавляем:
            cv.put(DataBase.TITLE, link);
            db.insert(DataBase.TITLE, null, cv);
        }
    }
}