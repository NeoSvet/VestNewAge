package ru.neosvet.utils;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;

import java.io.File;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ru.neosvet.vestnewage.MainActivity;
import ru.neosvet.vestnewage.SearchFragment;

public class SearchTask extends AsyncTask<String, Long, Boolean> implements Serializable {
    private transient SearchFragment frm;
    private transient MainActivity act;
    private boolean boolStart = true;
    //    private String msg;
    private DataBase dbSearch;
    private SQLiteDatabase dbS;

    public SearchTask(SearchFragment frm) {
        setFrm(frm);
    }

    public void setFrm(SearchFragment frm) {
        this.frm = frm;
        act = (MainActivity) frm.getActivity();
    }

    public void stop() {
        boolStart = false;
    }

    @Override
    protected void onProgressUpdate(Long... values) {
        super.onProgressUpdate(values);
        if (frm != null)
            frm.updateStatus(values[0]);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);
        if (frm != null)
            frm.showResult(result);
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
            if (list.size() == 0) //empty list
                return false;
            dbSearch = new DataBase(act, DataBase.SEARCH);
            dbS = dbSearch.getWritableDatabase();
            dbS.delete(DataBase.SEARCH, null, null);
            int sy, sm, ey, em, step, mode;
            mode = Integer.parseInt(params[1]);
            String s = params[2]; // начальная дата
            sm = Integer.parseInt(s.substring(0, 2)) - 1;
            sy = Integer.parseInt(s.substring(3, 5));
            s = params[3]; // конечная дата
            em = Integer.parseInt(s.substring(0, 2)) - 1;
            ey = Integer.parseInt(s.substring(3, 5));
            s = params[0]; // строка для поиска
            if ((sy == ey && sm <= em) || sy < ey)
                step = 1;
            else
                step = -1;
            Date d;
            DateFormat df = new SimpleDateFormat("MM.yy");
            if (mode == 3 && list.contains("00.00")) { //режим "по всем материалам"
                //поиск по материалам (статьям)
                searchList("00.00", s, mode);
            }
            while (boolStart) {
                d = new Date(sy, sm, 1);
                if (list.contains(df.format(d))) {
                    publishProgress(d.getTime());
                    searchList(df.format(d), s, mode);
                }
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
            dbSearch.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void searchList(String name, final String find, int mode) {
        DataBase dataBase = new DataBase(act, name);
        int n = Integer.parseInt(name.substring(3)) * 650 +
                Integer.parseInt(name.substring(0, 2)) * 50;
        SQLiteDatabase db = dataBase.getWritableDatabase();
        Cursor curSearch;
        if (mode == 2) { //Искать в заголовках
            curSearch = db.query(DataBase.TITLE, null,
                    DataBase.TITLE + DataBase.LIKE, new String[]{"%" + find + "%"}
                    , null, null, null);
        } else if (mode == 4) { //Искать по дате - ищем по ссылкам
            curSearch = db.query(DataBase.TITLE, null,
                    DataBase.LINK + DataBase.LIKE, new String[]{"%" + find + "%"}
                    , null, null, null);
        } else { //везде: 3 или 5 (по всем материалам или в Посланиях и Катренах)
            // фильтрация по 0 и 1 будет позже
            curSearch = db.query(DataBase.PARAGRAPH, null,
                    DataBase.PARAGRAPH + DataBase.LIKE, new String[]{"%" + find + "%"}
                    , null, null, null);
        }
        if (curSearch.moveToFirst()) {
            int iPar = curSearch.getColumnIndex(DataBase.PARAGRAPH);
            int iID = curSearch.getColumnIndex(DataBase.ID);
            String t, s;
            ContentValues cv = null;
            int id = -1;
            Cursor curTitle;
            boolean boolAdd = true;
            StringBuilder des = null;
            do {
                if (id == curSearch.getInt(iID) && boolAdd) {
                    des.append("<br><br>");
                    des.append(getDes(curSearch.getString(iPar), find));
                } else {
                    id = curSearch.getInt(iID);
                    curTitle = db.query(DataBase.TITLE, null,
                            DataBase.ID + DataBase.Q,
                            new String[]{String.valueOf(id)},
                            null, null, null);
                    if (curTitle.moveToFirst()) {
                        s = curTitle.getString(curTitle.getColumnIndex(DataBase.LINK));
                        if (mode == 0) //Искать в Посланиях
                            boolAdd = !s.contains(Lib.POEMS);
                        else if (mode == 1) //Искать в Катренах
                            boolAdd = s.contains(Lib.POEMS);
                        if (boolAdd) {
                            t = dataBase.getPageTitle(curTitle.getString(curTitle.getColumnIndex(DataBase.TITLE)), s);
                            if (cv != null) {
                                if (des != null) {
                                    cv.put(DataBase.DESCTRIPTION, des.toString());
                                    des = null;
                                }
                                dbS.insert(DataBase.SEARCH, null, cv);
                                cv = null;
                            }
                            cv = new ContentValues();
                            cv.put(DataBase.TITLE, t);
                            cv.put(DataBase.LINK, s);
                            cv.put(DataBase.ID, n);
                            n++;
                            if (iPar > -1) //если нужно добавлять абзац (при поиске в заголовках и датах не надо)
                                des = new StringBuilder(getDes(curSearch.getString(iPar), find));
                        }
                    }
                    curTitle.close();
                }
            } while (curSearch.moveToNext());
            if (cv != null) {
                if (des != null)
                    cv.put(DataBase.DESCTRIPTION, des.toString());
                dbS.insert(DataBase.SEARCH, null, cv);
            }
        }
        curSearch.close();
        dataBase.close();
    }

    private String getDes(String d, String sel) {
        d = act.lib.withOutTags(d);
        StringBuilder b = new StringBuilder(d);
        d = d.toLowerCase();
        sel = sel.toLowerCase();
        int i = -1, x = 0;
        while ((i = d.indexOf(sel, i + 1)) > -1) {
            b.insert(i + x + sel.length(), "</b></font>");
            b.insert(i + x, "<font color='#99ccff'><b>");
            x += 36;
        }
        return b.toString();
    }
}
