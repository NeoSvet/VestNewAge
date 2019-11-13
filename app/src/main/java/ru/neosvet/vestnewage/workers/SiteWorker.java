package ru.neosvet.vestnewage.workers;

import android.content.Context;
import android.support.annotation.NonNull;

import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.ProgressModel;
import ru.neosvet.vestnewage.fragment.SiteFragment;
import ru.neosvet.vestnewage.list.ListItem;
import ru.neosvet.vestnewage.model.LoaderModel;
import ru.neosvet.vestnewage.model.SiteModel;

public class SiteWorker extends Worker {
    private Context context;
    private ProgressModel model;
    List<ListItem> list = new ArrayList<ListItem>();

    public SiteWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
    }

    private boolean isCancelled() {
        if (model == null)
            return false;
        else
            return !model.inProgress;
    }

    @NonNull
    @Override
    public Result doWork() {
        String error, s;
        s = getInputData().getString(ProgressModel.NAME);
        model = ProgressModel.getModelByName(s);
        try {
            if (s.equals(SiteModel.class.getSimpleName())) {
                loadList(getInputData().getString(Const.LINK));
                s = getInputData().getString(Const.FILE);
                saveList(s);
                model.postProgress(new Data.Builder()
                        .putBoolean(Const.FINISH, true)
                        .putString(Const.FILE, s.substring(s.lastIndexOf("/")))
                        .build());
                return Result.success();
            }
            //loader
            String[] url = new String[]{
                    Const.SITE,
                    Const.SITE + "novosti.html",
            };
            Lib lib = new Lib(context);
            String[] file = new String[]{
                    lib.getFileByName(SiteFragment.MAIN).toString(),
                    lib.getFileByName(SiteFragment.NEWS).toString()
            };
            Data progUp = new Data.Builder()
                    .putInt(Const.DIALOG, LoaderModel.DIALOG_UP)
                    .build();
            for (int i = 0; i < url.length && !isCancelled(); i++) {
                loadList(url[i]);
                saveList(file[i]);
                model.postProgress(progUp);
            }
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            error = e.getMessage();
            Lib.LOG("SiteWolker error: " + error);
        }
        model.postProgress(new Data.Builder()
                .putBoolean(Const.FINISH, true)
                .putString(Const.ERROR, error)
                .build());
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
        String line;
        url += Const.PRINT;
        Lib lib = new Lib(context);
        InputStream in = new BufferedInputStream(lib.getStream(url));
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        boolean begin = false;
        int i, n;
        String s, d = "";
        String[] m;
        while ((line = br.readLine()) != null) {
            if (isCancelled())
                break;
            if (!begin) {
                begin = line.contains("h2") || line.contains("h3");//razdel
            }
            if (begin) {
                if (line.contains("<h")) {
                    if (line.contains(Const.AND)) continue;
                    setDes(d);
                    d = "";
                    if (line.contains("h3")) {
                        line = Lib.withOutTags(line);
                        if (line.length() > 5) {
                            list.add(new ListItem(line));
                            addLink("", "@");
                        }
                    } else
                        list.add(new ListItem(Lib.withOutTags(line), true));
                } else if (line.contains("href")) {
                    m = line.split("<br />");
                    for (i = 0; i < m.length; i++) {
                        n = 0;
                        line = Lib.withOutTags(m[i]);
                        if (line.length() < 5 || line.contains(">>")) continue;
                        setDes(d);
                        d = "";
                        list.add(new ListItem(line));
                        if (m[i].contains(Const.HREF)) {
                            while ((n = m[i].indexOf(Const.HREF, n)) > 0) {
                                n += 6;
                                s = m[i].substring(n, m[i].indexOf(">", n) - 1);
                                if (s.contains("..")) s = s.substring(2);
                                n = m[i].indexOf(">", n) + 1;
                                addLink(Lib.withOutTags(m[i].substring(n, m[i].indexOf("<", n))), s);
                            } // links
                        } else
                            addLink("", "@");
                    } // lines
                } else if (line.contains("<p")) {
                    line = Lib.withOutTags(line).replace("</p>", Const.BR).replace(Const.N, "");
                    if (line.length() > 5) {
                        d += line + Const.BR;
                    }
                } else {
                    if (line.contains("page-title"))
                        break;
                }
            }
        }
        setDes(d);
        br.close();
        in.close();
    }

    private void addLink(String head, String link) {
        if (link.contains(" ")) {
            //link" target="_blank
            link = link.substring(0, link.indexOf(" ") - 1);
        }
        if (link.contains("files") || link.contains(".mp3") || link.contains(".wma")
                || link.lastIndexOf("/") == link.length() - 1)
            link = Const.SITE + link.substring(1);
//        if (link.contains(Lib.HTML) && !link.contains("http"))
//            link = link.substring(0, link.length() - 5);
        if (link.indexOf("/") == 0)
            link = link.substring(1);
        list.get(list.size() - 1).addLink(head, link);
    }

    private void setDes(String d) {
        if (list.size() > 0) {
            if (!d.equals("")) {
                d = d.substring(0, d.length() - 4);
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
        File f = LoaderModel.getFileList(context);
        BufferedWriter bw = new BufferedWriter(new FileWriter(f, true));
        String s;
        int k = 0;
        while ((s = br.readLine()) != null) {
            if (s.contains(Const.HTML)) {
                bw.write(s);
                bw.newLine();
                k++;
            }
        }
        bw.close();
        br.close();
        return k;
    }
}
