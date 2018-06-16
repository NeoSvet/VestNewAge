package ru.neosvet.vestnewage.task;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.fragment.SearchFragment;
import ru.neosvet.vestnewage.helpers.DateHelper;

public class SearchTask extends AsyncTask<String, Integer, Boolean> implements Serializable {
    private transient SearchFragment frm;
    private transient MainActivity act;
    private boolean start = true;
    //    private String msg;
    private DataBase dbSearch;
    private SQLiteDatabase dbS;
    private String str;
    private int mode, count1 = 0, count2 = 0;

    public SearchTask(SearchFragment frm) {
        setFrm(frm);
    }

    public void setFrm(SearchFragment frm) {
        this.frm = frm;
        act = (MainActivity) frm.getActivity();
    }

    public void stop() {
        start = false;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
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
            frm.putResult(mode, str, count1, count2);
    }

    @Override
    protected Boolean doInBackground(String... params) {
        try {
            List<String> list = new ArrayList<String>();
            for (File f : act.lib.getDBFolder().listFiles()) {
                if (f.getName().length() == 5)
                    list.add(f.getName());
                if (!start) return true;
            }
            if (list.size() == 0) //empty list
                return false;
            dbSearch = new DataBase(act, DataBase.SEARCH);
            dbS = dbSearch.getWritableDatabase();
            int start_year, start_month, end_year, end_month, step;
            mode = Integer.parseInt(params[1]);
            str = params[2]; // начальная дата
            start_month = Integer.parseInt(str.substring(0, 2)) - 1;
            start_year = Integer.parseInt(str.substring(3, 5));
            str = params[3]; // конечная дата
            end_month = Integer.parseInt(str.substring(0, 2)) - 1;
            end_year = Integer.parseInt(str.substring(3, 5));
            str = params[0]; // строка для поиска
            if ((start_year == end_year && start_month <= end_month) || start_year < end_year)
                step = 1;
            else
                step = -1;
            if (mode == 6) { // поиск в результатах
                searchInResults(params[0], step == -1);
                dbSearch.close();
                return true;
            }
            dbS.delete(DataBase.SEARCH, null, null);
            DateHelper d;
            if (mode == 3 && list.contains("00.00")) { //режим "по всем материалам"
                //поиск по материалам (статьям)
                searchList("00.00", str, mode);
            }
            d = DateHelper.newBuilder(act).setYearMonth(start_year, start_month).build();
            while (start) {
                if (list.contains(d.getMY())) {
                    publishProgress(d.getTimeInDays());
                    searchList(d.getMY(), str, mode);
                }
                if (d.getYear() == end_year && d.getMonth() == end_month)
                    break;
                d.changeMonth(step);
            }
            dbSearch.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void searchInResults(String find, boolean reverseOrder) throws Exception {
        List<String> title = new ArrayList<String>();
        List<String> link = new ArrayList<String>();
        List<String> id = new ArrayList<String>();
        Cursor curSearch = dbS.query(DataBase.SEARCH,
                null, null, null, null, null,
                DataBase.ID + (reverseOrder ? DataBase.DESC : ""));
        if (curSearch.moveToFirst()) {
            int iTitle = curSearch.getColumnIndex(DataBase.TITLE);
            int iLink = curSearch.getColumnIndex(DataBase.LINK);
            int iID = curSearch.getColumnIndex(DataBase.ID);
            do {
                title.add(curSearch.getString(iTitle));
                link.add(curSearch.getString(iLink));
                id.add(String.valueOf(curSearch.getInt(iID)));
            } while (curSearch.moveToNext());
        }
        curSearch.close();
        DataBase dataBase = null;
        SQLiteDatabase db = null;
        Cursor cursor;
        String name1, name2 = "";
        ContentValues cv;
        StringBuilder des;
        for (int i = 0; i < title.size(); i++) {
            name1 = DataBase.getDatePage(link.get(i));
            if (!name1.equals(name2)) {
                if (dataBase != null)
                    dataBase.close();
                dataBase = new DataBase(act, name1);
                db = dataBase.getWritableDatabase();
            }
            cursor = db.query(DataBase.PARAGRAPH, new String[]{DataBase.PARAGRAPH},
                    DataBase.ID + DataBase.Q + " AND " + DataBase.PARAGRAPH + DataBase.LIKE,
                    new String[]{String.valueOf(dataBase.getPageId(link.get(i))), "%" + find + "%"},
                    null, null, null);
            if (cursor.moveToFirst()) {
                cv = new ContentValues();
                cv.put(DataBase.TITLE, title.get(i));
                cv.put(DataBase.LINK, link.get(i));
                des = new StringBuilder(getDes(cursor.getString(0), find));
                count2++;
                while (cursor.moveToNext()) {
                    des.append(Const.BR + Const.BR);
                    des.append(getDes(cursor.getString(0), find));
                }
                cv.put(DataBase.DESCTRIPTION, des.toString());
                dbS.update(DataBase.SEARCH, cv, DataBase.ID +
                        DataBase.Q, new String[]{id.get(i)});
            } else
                dbS.delete(DataBase.SEARCH, DataBase.ID +
                        DataBase.Q, new String[]{id.get(i)});
            cursor.close();
        }
        if (dataBase != null)
            dataBase.close();
        title.clear();
        link.clear();
        id.clear();
    }

    private void searchList(String name, final String find, int mode) throws Exception {
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
            boolean add = true;
            StringBuilder des = null;
            do {
                if (id == curSearch.getInt(iID) && add) {
                    des.append(Const.BR + Const.BR);
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
                            add = !s.contains(Const.POEMS);
                        else if (mode == 1) //Искать в Катренах
                            add = s.contains(Const.POEMS);
                        if (add) {
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
                            count2++;
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
        d = Lib.withOutTags(d);
        StringBuilder b = new StringBuilder(d);
        d = d.toLowerCase();
        sel = sel.toLowerCase();
        int i = -1, x = 0;
        while ((i = d.indexOf(sel, i + 1)) > -1) {
            b.insert(i + x + sel.length(), "</b></font>");
            b.insert(i + x, "<font color='#99ccff'><b>");
            x += 36;
            count1++;
        }
        return b.toString().replace(Const.N, Const.BR);
    }
}
