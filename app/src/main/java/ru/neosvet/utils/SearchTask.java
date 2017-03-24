package ru.neosvet.utils;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;

import java.io.File;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import ru.neosvet.ui.ListItem;
import ru.neosvet.vestnewage.MainActivity;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.SearchFragment;

public class SearchTask extends AsyncTask<String, String, Boolean> implements Serializable {
    private transient SearchFragment frm;
    private transient MainActivity act;
    private transient ProgressDialog di;
    private boolean boolStart = true;
    private String msg;
    List<ListItem> data = new ArrayList<ListItem>();

    public SearchTask(SearchFragment frm) {
        setFrm(frm);
    }

    public void setFrm(SearchFragment frm) {
        this.frm = frm;
        act = (MainActivity) frm.getActivity();
    }

    @Override
    protected void onProgressUpdate(String... values) {
        super.onProgressUpdate(values);
        msg = values[0];
        di.setMessage(msg);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        msg = act.getResources().getString(R.string.start);
        showD();
    }

    private void showD() {
        di = new ProgressDialog(act);
        di.setTitle(act.getResources().getString(R.string.search));
        di.setMessage(msg);
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
        if (di != null)
            di.dismiss();
        if (frm != null) {
            if (!result) data.clear();
            frm.finishSearch(data);
        }
    }

    @Override
    protected Boolean doInBackground(String... params) {
        try {
            List<String> list = new ArrayList<String>();
            for (File f : act.lib.getDBFolder().listFiles()) {
                if (f.getName().length() == 5)
                    list.add(f.getName());
                if (!boolStart) return true;
            }
            if (list.size() == 0) {
                //empty list
                return false;
            }
            int sy, sm, ey, em, step;
            String s = params[1]; // начальная дата
            sm = Integer.parseInt(s.substring(0, 2)) - 1;
            sy = Integer.parseInt(s.substring(3, 5));
            s = params[2]; // конечная дата
            em = Integer.parseInt(s.substring(0, 2)) - 1;
            ey = Integer.parseInt(s.substring(3, 5));
            s = params[0]; // строка для поиска
            if ((sy == ey && sm <= em) || sy < ey)
                step = 1;
            else
                step = -1;
            Date d;
            DateFormat df = new SimpleDateFormat("MM.yy");
            while (boolStart) {
                d = new Date(sy, sm, 1);
                if (list.contains(df.format(d))) {
                    publishProgress(act.getResources().getStringArray(R.array.months)
                            [d.getMonth()] + " " + (1900 + d.getYear()));
                    searchList(df.format(d), s, step == -1);
                }
//                if (data.size() > 5) break; tut
                if (sy == ey && sm == em)
                    break;
                sm += step;
                if (sm == 12) {
                    sm = 0;
                    sy++;
                } else if (sm == -1) {
                    sm = 11;
                    sy--;
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void searchList(String name, String s, boolean boolDesc) {
        DataBase dataBase = new DataBase(act, name);
        SQLiteDatabase db = dataBase.getWritableDatabase();
        Cursor curPar = db.query(DataBase.PARAGRAPH, null,
                DataBase.PARAGRAPH + DataBase.LIKE, new String[]{"%" + s + "%"}
                , null, null, null);
        final int size = data.size();
        if (curPar.moveToFirst()) {
            int iPar = curPar.getColumnIndex(DataBase.PARAGRAPH);
            int iID = curPar.getColumnIndex(DataBase.ID);
            String t;
            int i = size - 1, id = -1;
            Cursor curTitle;
            ListItem item;
            do {
                if (id == curPar.getInt(iID)) {
                    data.get(i).setDes(data.get(i).getDes()
                            + Lib.NN + act.lib.withOutTags(curPar.getString(iPar)));
                } else {
                    id = curPar.getInt(iID);
                    curTitle = db.query(DataBase.TITLE, null,
                            DataBase.ID + DataBase.Q,
                            new String[]{String.valueOf(id)},
                            null, null, null);
                    if (curTitle.moveToFirst()) {
                        s = curTitle.getString(curTitle.getColumnIndex(DataBase.LINK));
                        t = dataBase.getPageTitle(curTitle.getString(curTitle.getColumnIndex(DataBase.TITLE)), s);
                        item = new ListItem(t, s);
                        item.setDes(act.lib.withOutTags(curPar.getString(iPar)));
                        data.add(item);
                        i++;
                    }
                    curTitle.close();
                }
            } while (curPar.moveToNext());
        }
        curPar.close();
        dataBase.close();
        // сортировка списка:
        if (data.size() > size) {
            String s1, s2;
            String[] m;
            for (int a = size; a < data.size(); a++) {
                s1 = getDateLink(data.get(a).getLink());
                for (int b = a + 1; b < data.size(); b++) {
                    s2 = getDateLink(data.get(b).getLink());
                    if (s1.equals(s2)) continue;
                    m = new String[]{s1, s2};
                    Arrays.sort(m);
                    if ((!boolDesc && m[0].equals(s2)) ||
                            (boolDesc && m[0].equals(s1))) {
                        data.add(a, data.get(b));
                        data.remove(b + 1);
                        s1 = s2;
                    }
                }
            }
        }
    }

    private String getDateLink(String link) {
        return link.substring(link.indexOf(".", link.lastIndexOf("/")) - 2, link.lastIndexOf("."));
    }
}
