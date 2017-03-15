package ru.neosvet.utils;

import android.content.ContentValues;
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

public class BookTask extends AsyncTask<Byte, Void, String> implements Serializable {
    private transient BookFragment frm;
    private transient MainActivity act;
    private List<String> title = new ArrayList<String>();
    private List<String> links = new ArrayList<String>();

    public BookTask(BookFragment frm) {
        setFrm(frm);
    }

    public BookTask(MainActivity act) {
        this.act = act;
    }

    public void setFrm(BookFragment frm) {
        this.frm = frm;
        act = (MainActivity) frm.getActivity();
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        if (frm != null) {
            frm.finishLoad(result);
        }
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected void onProgressUpdate(Void... values) {
        super.onProgressUpdate(values);
    }

    @Override
    protected String doInBackground(Byte... params) {
        try {
            return downloadData(params[0] == 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public String downloadData(boolean boolKat) throws Exception {
        String url = Lib.SITE + (boolKat ? Lib.POEMS : "tolkovaniya") + Lib.PRINT;
        InputStream in = new BufferedInputStream(act.lib.getStream(url));
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        boolean b = false;
        int i, n;
        String line, t, s, date1 = "", date2;
        while ((line = br.readLine()) != null) {
            if (!b) {
                b = line.contains("h2");//razdel
            } else if (line.contains(Lib.HREF)) {
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
        return date1;
    }

    private void saveData(String date) throws Exception {
        if (title.size() > 0) {
            DataBase dataBase = new DataBase(act, date);
            SQLiteDatabase db = dataBase.getWritableDatabase();
            String s;
            if (links.get(0).contains(Lib.POEMS))
                s = Lib.POEMS;
            else
                s = "2016";
            db.delete(DataBase.TITLE, DataBase.LINK +
                    " LIKE ?", new String[]{"%" + s + "%"});

            ContentValues cv = new ContentValues();
            cv.put(DataBase.TIME, System.currentTimeMillis());
            if (db.update(DataBase.TITLE, cv,
                    DataBase.ID + DataBase.Q, new String[]{"1"}) == 0) {
                db.insert(DataBase.TITLE, null, cv);
            }

            for (int i = 0; i < title.size(); i++) {
                cv = new ContentValues();
                cv.put(DataBase.LINK, links.get(i));
                cv.put(DataBase.TITLE, title.get(i));
                db.insert(DataBase.TITLE, null, cv);
            }
            dataBase.close();
            title.clear();
            links.clear();
        }
    }
}
