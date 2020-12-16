package ru.neosvet.vestnewage.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.PageParser;
import ru.neosvet.vestnewage.fragment.SiteFragment;
import ru.neosvet.vestnewage.helpers.LoaderHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.list.ListItem;
import ru.neosvet.vestnewage.model.SiteModel;

public class SiteWorker extends Worker {
    private final Context context;
    private final List<ListItem> list = new ArrayList<>();
    private static final Pattern patternList = Pattern.compile("\\d{4}\\.html");

    public SiteWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        boolean SITE = getInputData().getString(Const.TASK).equals(SiteModel.class.getSimpleName());
        String error;
        try {
            if (SITE) {
                ProgressHelper.setBusy(true);
                ProgressHelper.postProgress(new Data.Builder()
                        .putBoolean(Const.START, true)
                        .build());
                loadList(getInputData().getString(Const.LINK));
                String s = getInputData().getString(Const.FILE);
                saveList(s);
                ProgressHelper.postProgress(new Data.Builder()
                        .putBoolean(Const.LIST, true)
                        .putString(Const.FILE, s.substring(s.lastIndexOf("/")))
                        .build());
                return Result.success();
            }
            //LoaderHelper
            if (!LoaderHelper.start)
                return Result.success();
            String[] url = new String[]{
                    Const.SITE,
                    Const.SITE + "novosti.html",
            };
            Lib lib = new Lib(context);
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
            error = e.getMessage();
        }
        if (SITE) {
            ProgressHelper.postProgress(new Data.Builder()
                    .putBoolean(Const.FINISH, true)
                    .putString(Const.ERROR, error)
                    .build());
        } else {
            LoaderHelper.postCommand(context, LoaderHelper.STOP, error);
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
        PageParser page = new PageParser(context);
        boolean boolSite = url.equals(Const.SITE);
        if (boolSite) {
            page.load(url, "page-title");
        } else {
            int i = url.lastIndexOf("/") + 1;
            url = url.substring(0, i) + Const.PRINT + url.substring(i);
            page.load(url, "razdel");
        }

        String s = page.getFirstElem();
        if (s == null) return;
        String a;
        StringBuilder d = new StringBuilder();
        do {
            if (page.isHead()) {
                if (boolSite)
                    list.add(new ListItem(page.getText(), true));
                else {
                    setDes(d.toString());
                    d = new StringBuilder();
                    list.add(new ListItem(page.getText()));
                    addLink("", "@");
                }
            } else {
                a = page.getLink();
                if (boolSite) {
                    if (page.getText().length() > 0) {
                        if (!s.contains("<"))
                            list.get(list.size() - 1).setDes(s);
                        else
                            list.add(new ListItem(page.getText()));
                        if (a != null)
                            addLink(page.getText(), a);
                    }
                } else {
                    if (a != null)
                        addLink(page.getText(), a);
                    d.append(s);
                }
            }
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

    public static int getListLink(Context context, String file) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(file));
        File f = LoaderHelper.getFileList(context);
        BufferedWriter bw = new BufferedWriter(new FileWriter(f, true));
        String s;
        int k = 0;
        while ((s = br.readLine()) != null) {
            if (isNeedLoad(s)) {
                if (s.contains("@"))
                    bw.write(s.substring(9));
                else
                    bw.write(s);
                bw.newLine();
                bw.flush();
                k++;
            }
        }
        bw.close();
        br.close();
        return k;
    }

    private static boolean isNeedLoad(String link) {
        if (!link.contains(Const.HTML))
            return false;
        if (link.contains("tolkovaniya") || (link.contains("/") && link.length() < 18))
            return false;
        if (patternList.matcher(link).matches())
            return false;
        return true;
    }
}
