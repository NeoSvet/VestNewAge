package ru.neosvet.vestnewage.workers;

import android.content.Context;
import android.support.annotation.NonNull;

import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.ProgressModel;
import ru.neosvet.vestnewage.fragment.SiteFragment;
import ru.neosvet.vestnewage.list.ListItem;

public class SiteWorker extends Worker {
    private Context context;
    public static final String TAG = "site";
    private ProgressModel model;
    List<ListItem> data = new ArrayList<ListItem>();

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
        String err = "";
        model = ProgressModel.getModelByName(getInputData().getString(ProgressModel.NAME));
        try {
            downloadList(getInputData().getString(Const.LINK));
            String name = getInputData().getString(Const.TITLE);
            if (!isCancelled())
                saveList(name);
            Data data = new Data.Builder()
                    .putString(Const.TITLE, name.substring(name.lastIndexOf("/")))
                    .build();
            return Result.success(data);
        } catch (Exception e) {
            e.printStackTrace();
            err = e.getMessage();
            Lib.LOG("SiteWolker error: " + err);
        }
        Data data = new Data.Builder()
                .putString(Const.TITLE, null)
                .putString(Const.ERROR, err)
                .build();
        return Result.failure(data);
    }

    private void saveList(String file) throws Exception {
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
        int j;
        for (int i = 0; i < data.size(); i++) {
//                if(item.getLink().equals("")) return null;
            bw.write(data.get(i).getTitle() + Const.N);
            bw.write(data.get(i).getDes() + Const.N);
            if (data.get(i).getCount() == 1)
                bw.write(data.get(i).getLink() + Const.N);
            else if (data.get(i).getCount() == 0)
                bw.write("@" + Const.N);
            else {
                for (j = 0; j < data.get(i).getCount(); j++) {
                    bw.write(data.get(i).getLink(j) + Const.N);
                    bw.write(data.get(i).getHead(j) + Const.N);
                }
            }
            bw.write(SiteFragment.END + Const.N);
            bw.flush();
        }
        bw.close();
        data.clear();
    }

    private void downloadList(String url) throws Exception {
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
            if(isCancelled())
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
                            data.add(new ListItem(line));
                            addLink("", "@");
                        }
                    } else
                        data.add(new ListItem(Lib.withOutTags(line), true));
                } else if (line.contains("href")) {
                    m = line.split("<br />");
                    for (i = 0; i < m.length; i++) {
                        n = 0;
                        line = Lib.withOutTags(m[i]);
                        if (line.length() < 5 || line.contains(">>")) continue;
                        setDes(d);
                        d = "";
                        data.add(new ListItem(line));
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
        data.get(data.size() - 1).addLink(head, link);
    }

    private void setDes(String d) {
        if (data.size() > 0) {
            if (!d.equals("")) {
                d = d.substring(0, d.length() - 4);
                int i = data.size() - 1;
                if (data.get(i).getLink().equals("#"))
                    data.add(new ListItem(d));
                else
                    data.get(i).setDes(d);
            }
        }
    }
}
