package ru.neosvet.vestnewage.loader;

import android.content.ContentValues;

import ru.neosvet.html.PageParser;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.NeoClient;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.storage.PageStorage;

public class PageLoader {
    private final boolean withMsg; //name.contains(LoaderModel.TAG)
    private int k_requests = 0;
    private long time_requests = 0;

    public PageLoader(boolean withMsg) {
        this.withMsg = withMsg;
    }

    public boolean download(String link, boolean singlePage) throws Exception {
        // если singlePage=true, значит страницу страницу перезагружаем, а счетчики обрабатываем
        if (withMsg)
            ProgressHelper.setMessage(initMessage(link));
        PageStorage storage = new PageStorage(link);
        if (!singlePage && storage.existsPage(link)) {
            storage.close();
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
        boolean boolArticle = storage.isArticle();
        PageParser page = new PageParser();
        if (NeoClient.isMainSite())
            page.load(NeoClient.SITE + Const.PRINT + s, "page-title");
        else
            page.load(NeoClient.SITE2 + Const.PRINT + s, "<h2>");

        ContentValues row;
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
                    id = storage.getPageId(link);
                    row = new ContentValues();
                    row.put(Const.TIME, System.currentTimeMillis());

                    if (id == -1) { // id не найден, материала нет - добавляем
                        if (link.contains("#")) {
                            id = bid;
                            row = new ContentValues();
                            row.put(DataBase.ID, id);
                            row.put(DataBase.PARAGRAPH, s);
                            storage.insertParagraph(row);
                        } else {
                            row.put(Const.TITLE, getTitle(s, storage.getName()));
                            row.put(Const.LINK, link);
                            id = (int) storage.insertTitle(row);
                            //обновляем дату изменения списка:
                            row = new ContentValues();
                            row.put(Const.TIME, System.currentTimeMillis());
                            storage.updateTitle(1, row);
                        }
                    } else { // id найден, значит материал есть
                        //обновляем заголовок
                        row.put(Const.TITLE, getTitle(s, storage.getName()));
                        //обновляем дату загрузки материала
                        storage.updateTitle(id, row);
                        //удаляем содержимое материала
                        storage.deleteParagraphs(id);
                    }
                    bid = id;
                    s = page.getNextElem();
                }
            }
            if ((k == 0 || boolArticle) && !page.isEmpty()) {
                row = new ContentValues();
                row.put(DataBase.ID, id);
                row.put(DataBase.PARAGRAPH, s);
                storage.insertParagraph(row);
            }
            s = page.getNextElem();
        } while (s != null);

        storage.close();
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
            DateHelper d = DateHelper.parse(s);
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
