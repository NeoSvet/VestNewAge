package ru.neosvet.utils;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import ru.neosvet.vestnewage.BookFragment;
import ru.neosvet.vestnewage.MainActivity;
import ru.neosvet.vestnewage.R;

public class BookTask extends AsyncTask<Integer, Boolean, String> implements Serializable {
    private final String SITE = "http://o53xo.n52gw4tpozsw42lzmexgk5i.cmle.ru/";
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
            di.setMax(137);
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
        InputStream in;
        OutputStream out;
        byte[] buf = new byte[1024];
        int i, m = 8, y = 4;
        String name = "01.16";
        File f;
        while (y < 16 && boolStart) {
            name = (m < 10 ? "0" : "") + m + "." + (y < 10 ? "0" : "") + y;
            msg = act.getResources().getStringArray(R.array.months)[m - 1] + " " + (2000 + y);
            publishProgress(false);
            f = new File(act.lib.getDBFolder() + "/" + name);
            if (!f.exists()) {
                out = new FileOutputStream(f, false);
                in = new BufferedInputStream(act.lib.getStream("http://neosvet.ucoz.ru/databases_vna/" + name));
                while ((i = in.read(buf)) > 0) {
                    out.write(buf, 0, i);
                    out.flush();
                }
                out.close();
                in.close();
            }
            if (m == 12) {
                m = 1;
                y++;
            } else
                m++;
            prog++;
        }
        return name + (boolStart ? 1 : 0);
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
