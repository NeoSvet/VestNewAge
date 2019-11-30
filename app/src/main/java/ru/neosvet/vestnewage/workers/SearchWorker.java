package ru.neosvet.vestnewage.workers;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.model.SearchModel;

public class SearchWorker extends Worker {
    private Context context;
    private DataBase dbSearch;
    private SQLiteDatabase dbS;
    private String str;
    private int mode, count1 = 0, count2 = 0;

    public SearchWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        ProgressHelper.setBusy(true);
        String error;
        try {
            Lib lib = new Lib(context);
            List<String> list = new ArrayList<String>();
            for (File f : lib.getDBFolder().listFiles()) {
                if (f.getName().length() == 5)
                    list.add(f.getName());
                if (SearchModel.cancel)
                    return getResult();
            }
            if (list.size() == 0) //empty list
                return getResult();
            dbSearch = new DataBase(context, Const.SEARCH);
            dbS = dbSearch.getWritableDatabase();
            int start_year, start_month, end_year, end_month, step;
            mode = getInputData().getInt(Const.MODE, 0);
            str = getInputData().getString(Const.START); // начальная дата
            start_month = Integer.parseInt(str.substring(0, 2));
            start_year = Integer.parseInt(str.substring(3, 5));
            str = getInputData().getString(Const.END); // конечная дата
            end_month = Integer.parseInt(str.substring(0, 2));
            end_year = Integer.parseInt(str.substring(3, 5));
            str = getInputData().getString(Const.STRING); // строка для поиска
            if ((start_year == end_year && start_month <= end_month) || start_year < end_year)
                step = 1;
            else
                step = -1;
            if (mode == 6) { // поиск в результатах
                searchInResults(str, step == -1);
                dbSearch.close();
                return getResult();
            }
            dbS.delete(Const.SEARCH, null, null);
            DateHelper d;
            if (mode == 3 && list.contains("00.00")) { //режим "по всем материалам"
                //поиск по материалам (статьям)
                searchList("00.00", str, mode);
            }
            d = DateHelper.putYearMonth(context, start_year, start_month);
            while (!SearchModel.cancel) {
                if (list.contains(d.getMY())) {
                    publishProgress(d.getTimeInDays());
                    searchList(d.getMY(), str, mode);
                }
                if (d.getYear() == end_year && d.getMonth() == end_month)
                    break;
                d.changeMonth(step);
            }
            dbS.close();
            dbSearch.close();
            return getResult();
        } catch (Exception e) {
            e.printStackTrace();
            error = e.getMessage();
            Lib.LOG("SearchWolker error: " + error);
        }
        ProgressHelper.postProgress(new Data.Builder()
                .putBoolean(Const.FINISH, true)
                .putString(Const.ERROR, error)
                .build());
        return Result.failure();
    }

    private Result getResult() {
        ProgressHelper.postProgress(new Data.Builder()
                .putBoolean(Const.FINISH, true)
                .putInt(Const.MODE, mode)
                .putString(Const.STRING, str)
                .putInt(Const.START, count1)
                .putInt(Const.END, count2)
                .build());
        return Result.success();
    }

    private void publishProgress(int time) {
        ProgressHelper.postProgress(new Data.Builder()
                .putString(Const.MODE, Const.TIME)
                .putInt(Const.TIME, time)
                .build());
    }

    private void searchInResults(String find, boolean reverseOrder) throws Exception {
        List<String> title = new ArrayList<String>();
        List<String> link = new ArrayList<String>();
        List<String> id = new ArrayList<String>();
        Cursor curSearch = dbS.query(Const.SEARCH,
                null, null, null, null, null,
                DataBase.ID + (reverseOrder ? DataBase.DESC : ""));
        if (curSearch.moveToFirst()) {
            int iTitle = curSearch.getColumnIndex(Const.TITLE);
            int iLink = curSearch.getColumnIndex(Const.LINK);
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
        int p1 = -1, p2;
        for (int i = 0; i < title.size(); i++) {
            name1 = DataBase.getDatePage(link.get(i));
            if (!name1.equals(name2)) {
                if (dataBase != null) {
                    db.close();
                    dataBase.close();
                }
                dataBase = new DataBase(context, name1);
                db = dataBase.getWritableDatabase();
            }
            cursor = db.query(DataBase.PARAGRAPH, new String[]{DataBase.PARAGRAPH},
                    DataBase.ID + DataBase.Q + " AND " + DataBase.PARAGRAPH + DataBase.LIKE,
                    new String[]{String.valueOf(dataBase.getPageId(link.get(i))), "%" + find + "%"},
                    null, null, null);
            if (cursor.moveToFirst()) {
                cv = new ContentValues();
                cv.put(Const.TITLE, title.get(i));
                cv.put(Const.LINK, link.get(i));
                des = new StringBuilder(getDes(cursor.getString(0), find));
                count2++;
                while (cursor.moveToNext()) {
                    des.append(Const.BR + Const.BR);
                    des.append(getDes(cursor.getString(0), find));
                }
                cv.put(Const.DESCTRIPTION, des.toString());
                dbS.update(Const.SEARCH, cv, DataBase.ID +
                        DataBase.Q, new String[]{id.get(i)});
            } else {
                dbS.delete(Const.SEARCH, DataBase.ID +
                        DataBase.Q, new String[]{id.get(i)});
            }
            cursor.close();
            p2 = ProgressHelper.getProcent(i, title.size());
            if (p1 < p2) {
                p1 = p2;
                ProgressHelper.postProgress(new Data.Builder()
                        .putString(Const.MODE, Const.PROG)
                        .putInt(Const.PROG, p1)
                        .build());
            }
        }
        if (dataBase != null) {
            db.close();
            dataBase.close();
        }
        title.clear();
        link.clear();
        id.clear();
    }

    private void searchList(String name, final String find, int mode) throws Exception {
        DataBase dataBase = new DataBase(context, name);
        int n = Integer.parseInt(name.substring(3)) * 650 +
                Integer.parseInt(name.substring(0, 2)) * 50;
        SQLiteDatabase db = dataBase.getWritableDatabase();
        Cursor curSearch;
        if (mode == 2) { //Искать в заголовках
            curSearch = db.query(Const.TITLE, null,
                    Const.TITLE + DataBase.LIKE, new String[]{"%" + find + "%"}
                    , null, null, null);
        } else if (mode == 4) { //Искать по дате - ищем по ссылкам
            curSearch = db.query(Const.TITLE, null,
                    Const.LINK + DataBase.LIKE, new String[]{"%" + find + "%"}
                    , null, null, null);
        } else { //везде: 3 или 5 (по всем материалам или в Посланиях и Катренах)
            //фильтрация по 0 и 1 будет позже
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
                    curTitle = db.query(Const.TITLE, null,
                            DataBase.ID + DataBase.Q,
                            new String[]{String.valueOf(id)},
                            null, null, null);
                    if (curTitle.moveToFirst()) {
                        s = curTitle.getString(curTitle.getColumnIndex(Const.LINK));
                        if (mode == 0) //Искать в Посланиях
                            add = !s.contains(Const.POEMS);
                        else if (mode == 1) //Искать в Катренах
                            add = s.contains(Const.POEMS);
                        if (add) {
                            t = dataBase.getPageTitle(curTitle.getString(curTitle.getColumnIndex(Const.TITLE)), s);
                            if (cv != null) {
                                if (des != null) {
                                    cv.put(Const.DESCTRIPTION, des.toString());
                                    des = null;
                                }
                                dbS.insert(Const.SEARCH, null, cv);
                                cv = null;
                            }
                            cv = new ContentValues();
                            cv.put(Const.TITLE, t);
                            cv.put(Const.LINK, s);
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
                    cv.put(Const.DESCTRIPTION, des.toString());
                dbS.insert(Const.SEARCH, null, cv);
            }
        }
        curSearch.close();
        db.close();
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
