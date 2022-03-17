package ru.neosvet.vestnewage.loader;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import ru.neosvet.html.PageParser;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;

public class PageLoader {
    private final Context context;
    private final Lib lib;
    private final boolean withMsg; //name.contains(LoaderModel.TAG)
    private int k_requests = 0;
    private long time_requests = 0;

    public PageLoader(Context context, Lib lib, boolean withMsg) {
        this.context = context;
        this.lib = lib;
        this.withMsg = withMsg;
    }

    public boolean download(String link, boolean singlePage) throws Exception {
        // если singlePage=true, значит страницу страницу перезагружаем, а счетчики обрабатываем
        if (withMsg)
            ProgressHelper.setMessage(initMessage(link));
        DataBase dataBase = new DataBase(context, link);
        if (!singlePage && dataBase.existsPage(link)) {
            dataBase.close();
            return false;
        }
        if (!singlePage)
            checkRequests();
        String s = link;
        int k = 1;
        if (link.contains("#")) {
            k = Integer.parseInt(s.substring(s.indexOf("#") + 1));
            s = s.substring(0, s.indexOf("#"));
            if (link.contains("?")) s += link.substring(link.indexOf("?"));
        }

        int n = k;
        boolean boolArticle = dataBase.isArticle();
        PageParser page = new PageParser(context);
        if (lib.isMainSite())
            page.load(Const.SITE + Const.PRINT + s, "page-title");
        else
            page.load(Const.SITE2 + Const.PRINT + s, "<h2>");

        ContentValues cv;
        int id = 0, bid = 0;

        s = page.getFirstElem();
        do {
            if (page.isHead()) {
                k--;
                if (k == -1 && !boolArticle) {
                    n++;
                    if (link.contains("#"))
                        link = link.substring(0, link.indexOf("#"));
                    link += "#" + n;
                    k = 0;
                }
                if (k == 0) {
                    Cursor cursor = dataBase.query(Const.TITLE, new String[]{DataBase.ID, Const.TITLE}, Const.LINK + DataBase.Q, link);
                    if (cursor.moveToFirst())
                        id = cursor.getInt(0);
                    else id = 0;
                    cursor.close();
                    cv = new ContentValues();
                    cv.put(Const.TIME, System.currentTimeMillis());

                    if (id == 0) { // id не найден, материала нет - добавляем
                        if (link.contains("#")) {
                            id = bid;
                            cv = new ContentValues();
                            cv.put(DataBase.ID, id);
                            cv.put(DataBase.PARAGRAPH, s);
                            dataBase.insert(DataBase.PARAGRAPH, cv);
                        } else {
                            cv.put(Const.TITLE, getTitle(s, dataBase.getDatabaseName()));
                            cv.put(Const.LINK, link);
                            id = (int) dataBase.insert(Const.TITLE, cv);
                            //обновляем дату изменения списка:
                            cv = new ContentValues();
                            cv.put(Const.TIME, System.currentTimeMillis());
                            dataBase.update(Const.TITLE, cv, DataBase.ID + DataBase.Q, "1");
                        }
                    } else { // id найден, значит материал есть
                        //обновляем заголовок
                        cv.put(Const.TITLE, getTitle(s, dataBase.getDatabaseName()));
                        //обновляем дату загрузки материала
                        dataBase.update(Const.TITLE, cv, DataBase.ID + DataBase.Q, id);
                        //удаляем содержимое материала
                        dataBase.delete(DataBase.PARAGRAPH, DataBase.ID + DataBase.Q, id);
                    }
                    bid = id;
                    s = page.getNextElem();
                }
            }
            if ((k == 0 || boolArticle) && !page.isEmpty()) {
                cv = new ContentValues();
                cv.put(DataBase.ID, id);
                cv.put(DataBase.PARAGRAPH, s);
                dataBase.insert(DataBase.PARAGRAPH, cv);
            }
            s = page.getNextElem();
        } while (s != null);

        dataBase.close();
        page.clear();
        return true;
    }

    private String initMessage(String s) {
        if (!s.contains("/"))
            return s;
        try {
            s = s.substring(s.lastIndexOf("/") + 1, s.lastIndexOf("."));
            if (s.contains("_"))
                s = s.substring(0, s.length() - 2);
            DateHelper d = DateHelper.parse(context, s);
            return d.getMonthString() + " " + d.getYear();
        } catch (Exception ignored) {
        }
        return s;
    }

    private void checkRequests() {
        k_requests++;
        if (k_requests == 5) {
            long now = System.currentTimeMillis();
            k_requests = 0;
            if (now - time_requests < DateHelper.SEC_IN_MILLS) {
                try {
                    Thread.sleep(400);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            time_requests = now;
        }
    }

    private String getTitle(String line, String name) {
        line = Lib.withOutTags(line).replace(".20", ".");
        if (line.contains(name)) {
            line = line.substring(9);
            if (line.contains(Const.KV_OPEN))
                line = line.substring(line.indexOf(Const.KV_OPEN) + 1, line.length() - 1);
        }
        return line;
    }

}
