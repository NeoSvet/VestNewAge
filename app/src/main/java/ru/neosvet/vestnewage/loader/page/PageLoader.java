package ru.neosvet.vestnewage.loader.page;

import android.content.ContentValues;

import ru.neosvet.vestnewage.data.DataBase;
import ru.neosvet.vestnewage.data.DateUnit;
import ru.neosvet.vestnewage.network.NeoClient;
import ru.neosvet.vestnewage.storage.PageStorage;
import ru.neosvet.vestnewage.utils.Const;
import ru.neosvet.vestnewage.utils.Lib;

public class PageLoader {
    private int k_requests = 0;
    private long time_requests = 0;
    private final PageStorage storage = new PageStorage();

    public boolean download(String link, boolean singlePage) throws Exception {
        // если singlePage=true, значит страницу страницу перезагружаем, а счетчики обрабатываем
        storage.open(link);
        if (!singlePage && storage.existsPage(link)) {
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

        s = page.getCurrentElem();
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
            if ((k == 0 || boolArticle) && !isEmpty(s)) {
                row = new ContentValues();
                row.put(DataBase.ID, id);
                row.put(DataBase.PARAGRAPH, s);
                storage.insertParagraph(row);
            }
            s = page.getNextElem();
        } while (s != null);

        if (singlePage)
            storage.close();
        page.clear();
        return true;
    }

    private boolean isEmpty(String s) {
        return Lib.withOutTags(s).isEmpty();
    }

    private void checkRequests() {
        k_requests++;
        if (k_requests == 5) {
            long now = System.currentTimeMillis();
            k_requests = 0;
            if (now - time_requests < DateUnit.SEC_IN_MILLS) {
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

    public void finish() {
        storage.close();
    }
}