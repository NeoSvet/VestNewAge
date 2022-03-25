package ru.neosvet.vestnewage.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import ru.neosvet.html.PageParser;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.ErrorUtils;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.App;
import ru.neosvet.vestnewage.fragment.SiteFragment;
import ru.neosvet.vestnewage.helpers.DevadsHelper;
import ru.neosvet.vestnewage.helpers.LoaderHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.list.ListItem;
import ru.neosvet.vestnewage.model.SiteModel;

public class SiteWorker extends Worker {
    private final List<ListItem> list = new ArrayList<>();

    public SiteWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        boolean SITE = getInputData().getString(Const.TASK).equals(SiteModel.class.getSimpleName());
        String error;
        ErrorUtils.setData(getInputData());
        try {
            if (SITE) {
                ProgressHelper.setBusy(true);
                ProgressHelper.postProgress(new Data.Builder()
                        .putBoolean(Const.START, true)
                        .build());
                if (getInputData().getBoolean(Const.ADS, false)) {
                    DevadsHelper ads = new DevadsHelper(App.context);
                    ads.clear();
                    ads.loadAds();
                    ads.close();
                    ProgressHelper.postProgress(new Data.Builder()
                            .putBoolean(Const.ADS, true)
                            .build());
                } else {
                    loadList(getInputData().getString(Const.LINK));
                    String s = getInputData().getString(Const.FILE);
                    saveList(s);
                    ProgressHelper.postProgress(new Data.Builder()
                            .putBoolean(Const.LIST, true)
                            .putString(Const.FILE, s.substring(s.lastIndexOf("/")))
                            .build());
                }
                return Result.success();
            }
            //LoaderHelper
            if (!LoaderHelper.start)
                return Result.success();
            String[] url = new String[]{
                    Const.SITE,
                    Const.SITE + "novosti.html",
            };
            Lib lib = new Lib();
            String[] file = new String[]{
                    lib.getFileByName(SiteFragment.MAIN).toString(),
                    lib.getFileByName(SiteFragment.NEWS).toString()
            };
            for (int i = 0; i < url.length && !ProgressHelper.isCancelled(); i++) {
                loadList(url[i]);
                saveList(file[i]);
                ProgressHelper.upProg();
            }
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            ErrorUtils.setError(e);
            error = e.getMessage();
        }
        if (SITE) {
            ProgressHelper.postProgress(new Data.Builder()
                    .putBoolean(Const.FINISH, true)
                    .putString(Const.ERROR, error)
                    .build());
        } else {
            LoaderHelper.postCommand(LoaderHelper.STOP, error);
            return Result.failure();
        }
        return Result.failure();
    }

    private void saveList(String file) throws Exception {
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
        int j;
        for (int i = 0; i < list.size(); i++) {
//                if(item.getLink().equals("")) return null;
            bw.write(list.get(i).getTitle() + Const.N);
            bw.write(list.get(i).getDes() + Const.N);
            if (list.get(i).getCount() == 1)
                bw.write(list.get(i).getLink() + Const.N);
            else if (list.get(i).getCount() == 0)
                bw.write("@" + Const.N);
            else {
                for (j = 0; j < list.get(i).getCount(); j++) {
                    bw.write(list.get(i).getLink(j) + Const.N);
                    bw.write(list.get(i).getHead(j) + Const.N);
                }
            }
            bw.write(SiteFragment.END + Const.N);
            bw.flush();
        }
        bw.close();
        list.clear();
    }

    private void loadList(String url) throws Exception {
        PageParser page = new PageParser();
        boolean isSite = url.equals(Const.SITE);
        int i;
        if (isSite) {
            page.load(url, "page-title");
        } else {
            i = url.lastIndexOf("/") + 1;
            url = url.substring(0, i) + Const.PRINT + url.substring(i);
            page.load(url, "razdel");
        }
        String s = page.getFirstElem();
        if (s == null) return;
        String t, a;
        StringBuilder d = new StringBuilder();
        do {
            if (page.isHead()) {
                if (isSite)
                    list.add(new ListItem(page.getText(), true));
                else {
                    setDes(d.toString());
                    d = new StringBuilder();
                    list.add(new ListItem(page.getText()));
                    addLink("", "@");
                }
            } else {
                a = page.getLink();
                t = page.getText();
                if (isSite) {
                    if (!t.isEmpty() && !s.contains("\"#\"")) {
                        if (s.contains("&times;") || s.contains("<button>"))
                            break;
                        if (!s.contains("<"))
                            list.get(list.size() - 1).setDes(s);
                        else
                            list.add(new ListItem(t));
                        if (a != null)
                            addLink(t, a);
                    }
                } else {
                    if (t.isEmpty()) {
                        s = page.getNextItem();
                        if (s.contains("title=")) {
                            i = s.indexOf("title=") + 7;
                            t = s.substring(i, s.indexOf("\"", i));
                        } else if (s.contains("alt=")) {
                            i = s.indexOf("alt=") + 5;
                            t = s.substring(i, s.indexOf("\"", i));
                        }
                        s = "<a href='" + a + "'>" + t + "</a><br>";
                    }
                    if (a != null)
                        addLink(t, a);
                    d.append(s);
                }
            }
            s = page.getNextItem();
            while (!page.curItem().start && s != null)
                s = page.getNextItem();
        } while (s != null);
        setDes(d.toString());
        page.clear();
    }

    private void addLink(String head, String link) {
        if (link.contains("files") || link.contains(".mp3") || link.contains(".wma")
                || link.lastIndexOf("/") == link.length() - 1)
            link = Const.SITE + link.substring(1);
        if (link.indexOf("/") == 0)
            link = link.substring(1);
        list.get(list.size() - 1).addLink(head, link);
    }

    private void setDes(String d) {
        if (list.size() > 0) {
            if (!d.equals("")) {
                int i = list.size() - 1;
                if (list.get(i).getLink().equals("#"))
                    list.add(new ListItem(d));
                else
                    list.get(i).setDes(d);
            }
        }
    }

}