package ru.neosvet.vestnewage.workers;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.SummaryHelper;
import ru.neosvet.vestnewage.helpers.UnreadHelper;
import ru.neosvet.vestnewage.model.LoaderModel;
import ru.neosvet.vestnewage.service.CheckService;

public class CheckWorker extends Worker {
    private Context context;
    public static final String TAG_PERIODIC = "check periodic";
    //private final String LAST_TIME = "last_time";

    public CheckWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        String err;
        try {
            if (getInputData().getBoolean(Const.CHECK, false)) {
                Intent intent = new Intent(context, CheckService.class);
                context.startService(intent);
            } else {
                if(checkSummary()) {
                    SummaryHelper summaryHelper = new SummaryHelper(context);
                    summaryHelper.updateBook();
                }
            }
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            err = e.getMessage();
            Lib.LOG("CheckWorker error: " + err);
        }
        return Result.failure(new Data.Builder()
                .putString(Const.ERROR, err)
                .build());
    }

    private boolean checkSummary() throws Exception {
        Lib lib = new Lib(context);
        InputStream in = new BufferedInputStream(lib.getStream(Const.SITE
                + "rss/?" + System.currentTimeMillis()));
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        String s, title, link, des;
        s = br.readLine();
        while (!s.contains("item"))
            s = br.readLine();
        title = withOutTag(br.readLine());
        link = parseLink(br.readLine());
        des = withOutTag(br.readLine());
        s = withOutTag(br.readLine()); //time

        File file = new File(context.getFilesDir() + Const.RSS);
        long secFile = 0;
        if (file.exists())
            secFile = DateHelper.putMills(context, file.lastModified()).getTimeInSeconds();
        long secList = DateHelper.parse(context, s).getTimeInSeconds();
        if (secFile > secList) { //список в загрузке не нуждается
            br.close();
            return false;
        }
        BufferedWriter bwRSS = new BufferedWriter(new FileWriter(file));
        file = LoaderModel.getFileList(context);
        BufferedWriter bwList = new BufferedWriter(new FileWriter(file));
        UnreadHelper unread = new UnreadHelper(context);
        DateHelper d;
        do {
            d = DateHelper.parse(context, s);
            if (unread.addLink(link, d)) {
                bwList.write(link);
                bwList.newLine();
                bwList.flush();
                postItem(title, link);
            }
            bwRSS.write(title);
            bwRSS.write(Const.N);
            bwRSS.write(link);
            bwRSS.write(Const.N);
            bwRSS.write(des);
            bwRSS.write(Const.N);
            bwRSS.write(d.getTimeInMills() + Const.N); //time
            bwRSS.flush();
            s = br.readLine(); //</item><item> or </channel>
            if (s.contains("</channel>")) break;
            title = withOutTag(br.readLine());
            link = parseLink(br.readLine());
            des = withOutTag(br.readLine());
            s = withOutTag(br.readLine()); //time
        } while (s != null);
        bwRSS.close();
        br.close();
        in.close();
        if (unread.addLink(link, d)) {
            bwList.write(link);
            bwList.newLine();
            bwList.flush();
            postItem(title, link);
        }
        bwList.close();
        unread.setBadge();
        unread.close();
        return true;
    }

    private void postItem(String title, String link) {
        CheckService.progress.postValue(new Data.Builder()
                .putString(Const.TITLE, title)
                .putString(Const.LINK, link)
                .build());
    }

    private String withOutTag(String s) {
        int i = s.indexOf(">") + 1;
        s = s.substring(i, s.indexOf("<", i));
        return s;
    }

    private String parseLink(String s) {
        s = withOutTag(s);
        if (s.contains(Const.SITE2))
            s = s.substring(Const.SITE2.length());
        else if (s.contains(Const.SITE))
            s = s.substring(Const.SITE.length());
        return s;
    }
}
