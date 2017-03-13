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

import ru.neosvet.ui.ListItem;
import ru.neosvet.vestnewage.BookFragment;
import ru.neosvet.vestnewage.MainActivity;

public class BookTask extends AsyncTask<Byte, Void, Boolean> implements Serializable {
    private transient BookFragment frm;
    private transient MainActivity act;
    private List<ListItem> data = new ArrayList<ListItem>();

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
    protected void onPostExecute(Boolean result) {
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
    protected Boolean doInBackground(Byte... params) {
        try {
            downloadData(params[0] == 0);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public void downloadData(boolean boolKat) throws Exception {
        String url = Lib.SITE + (boolKat ? Lib.POEMS : "tolkovaniya") + Lib.PRINT;
        InputStream in = new BufferedInputStream(act.lib.getStream(url));
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        boolean b = false;
        int i, n;
        String line, t, s, f1 = "", f2;
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
                    f2 = s.substring(i, i + 5);
                    if (!f2.equals(f1)) {
                        saveData(f1);
                        f1 = f2;
                    }
                    t = line.substring(line.indexOf(">", n) + 1, line.indexOf("<", n));
                    if (t.contains("(")) //poems
                        t = t.substring(0, t.indexOf("("));
                    data.add(new ListItem(t, s));
                }
                saveData(f1);
            }
        }
    }

    private void saveData(String date) throws Exception {
        if (data.size() > 0) {
            DataBase dbTable = new DataBase(act, date);
            SQLiteDatabase db = dbTable.getWritableDatabase();
            ContentValues cv;
            for (int i = 0; i < data.size(); i++) {
                cv = new ContentValues();
                cv.put(DataBase.LINK, data.get(i).getLink());
                cv.put(DataBase.TITLE, data.get(i).getTitle());
                db.insert(DataBase.TITLE, null, cv);
            }
            dbTable.close();
            data.clear();
        }
    }
}
