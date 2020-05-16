package ru.neosvet.vestnewage.workers;

import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;

import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.helpers.CheckHelper;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.LoaderHelper;
import ru.neosvet.vestnewage.helpers.SummaryHelper;
import ru.neosvet.vestnewage.helpers.UnreadHelper;

public class CheckWorker extends Worker {
    private Context context;
    public static final String TAG_PERIODIC = "check periodic";
    private List<String> list = new ArrayList<>();

    public CheckWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            if (getInputData().getBoolean(Const.CHECK, false)) {
                CheckHelper.postCommand(context, true);
            } else {
                if (checkSummary()) {
                    SummaryHelper summaryHelper = new SummaryHelper(context);
                    summaryHelper.updateBook();
                    makeNotification();
                }
            }
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
        }
        CheckHelper.postCommand(context, false);
        return Result.failure();
    }

    private boolean checkSummary() throws Exception {
        Lib lib = new Lib(context);
        InputStream in = new BufferedInputStream(lib.getStream(Const.SITE
                + "rss/?" + System.currentTimeMillis()));
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        String s = br.readLine();
        br.close();
        in.close();
        int a = s.indexOf("lastBuildDate") + 14;
        long secList = DateHelper.parse(context, s.substring(a, s.indexOf("<", a))).getTimeInSeconds();

        File file = new File(context.getFilesDir() + Const.RSS);
        long secFile = 0;
        if (file.exists())
            secFile = DateHelper.putMills(context, file.lastModified()).getTimeInSeconds();
        if (secFile > secList) { //список в загрузке не нуждается
            br.close();
            return false;
        }
        BufferedWriter bwRSS = new BufferedWriter(new FileWriter(file));
        file = LoaderHelper.getFileList(context);
        BufferedWriter bwList = new BufferedWriter(new FileWriter(file));
        UnreadHelper unread = new UnreadHelper(context);
        DateHelper d;
        String title, link;
        int b;
        String m[] = s.split("<item>");
        for (int i = 1; i < m.length; i++) {
            a = m[i].indexOf("</link");
            link = withOutTag(m[i].substring(0, a));
            if (link.contains(Const.SITE.substring(8)))
                link = link.substring(link.indexOf("info/") + 5);
            if (link.contains("#0"))
                link = link.replace("#0", "#2");
            b = m[i].indexOf("</title");
            title = withOutTag(m[i].substring(a + 10, b));
            bwRSS.write(title); //title
            bwRSS.write(Const.N);
            bwRSS.write(link); //link
            bwRSS.write(Const.N);
            a = m[i].indexOf("</des");
            bwRSS.write(withOutTag(m[i].substring(b + 10, a))); //des
            bwRSS.write(Const.N);
            b = m[i].indexOf("</a10");
            s = withOutTag(m[i].substring(a + 15, b));
            d = DateHelper.parse(context, s);
            bwRSS.write(d.getTimeInMills() + Const.N); //time
            bwRSS.flush();
            if (unread.addLink(link, d)) {
                bwList.write(link);
                bwList.newLine();
                bwList.flush();
                postItem(title, link);
            }
        }
        bwRSS.close();
        unread.setBadge();
        unread.close();
        return true;
    }

    private void postItem(String title, String link) {
        list.add(title);
        list.add(link);
    }

    private String withOutTag(String s) {
        return s.substring(s.indexOf(">") + 1);
    }

    private void makeNotification() {
        if (list.size() == 0)
            return;
        SummaryHelper summaryHelper = new SummaryHelper(getApplicationContext());
        boolean several = list.size() > 2;
        boolean notNotify = several && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP;
        int start, end, step;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            start = 0;
            end = list.size();
            step = 2;
        } else {
            start = list.size() - 2;
            end = -2;
            step = -2;
        }
        for (int i = start; i != end; i += step) {
            if (summaryHelper.isNotification() && !notNotify)
                summaryHelper.showNotification();
            summaryHelper.createNotification(list.get(i), list.get(i + 1));
            if (several)
                summaryHelper.muteNotification();
        }
        if (several) {
            if (!notNotify)
                summaryHelper.showNotification();
            summaryHelper.groupNotification();
        } else
            summaryHelper.singleNotification(list.get(0));
        summaryHelper.setPreferences();
        summaryHelper.showNotification();
    }
}
