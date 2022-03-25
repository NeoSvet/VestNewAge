package ru.neosvet.vestnewage.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.ErrorUtils;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.NeoClient;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.LoaderHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.helpers.SummaryHelper;
import ru.neosvet.vestnewage.helpers.UnreadHelper;
import ru.neosvet.vestnewage.model.SummaryModel;

public class SummaryWorker extends Worker {
    private boolean SUMMARY;

    public SummaryWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        SUMMARY = getInputData().getString(Const.TASK).equals(SummaryModel.class.getSimpleName());
        String error;
        ErrorUtils.setData(getInputData());
        try {
            if (SUMMARY) {
                ProgressHelper.setBusy(true);
                ProgressHelper.postProgress(new Data.Builder()
                        .putBoolean(Const.START, true)
                        .build());
                loadList();
                SummaryHelper summaryHelper = new SummaryHelper();
                summaryHelper.updateBook();
                ProgressHelper.postProgress(new Data.Builder()
                        .putBoolean(Const.LIST, true)
                        .build());
                return Result.success();
            }
            //LoaderHelper
            if (!LoaderHelper.start)
                return Result.success();
            loadList();
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            ErrorUtils.setError(e);
            error = e.getMessage();
        }
        if (SUMMARY) {
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

    private String withOutTag(String s) {
        return s.substring(s.indexOf(">") + 1);
    }

    private void loadList() throws Exception {
        InputStream in = NeoClient.getStream(Const.SITE + "rss/?" + System.currentTimeMillis());
        String site;
        if (NeoClient.isMainSite())
            site = Const.SITE.substring(Const.SITE.indexOf("/") + 2);
        else
            site = Const.SITE2.substring(Const.SITE2.indexOf("/") + 2);
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        BufferedWriter bw = new BufferedWriter(new FileWriter(Lib.getFile(Const.RSS)));
        DateHelper now = DateHelper.initNow();
        UnreadHelper unread = new UnreadHelper();
        String[] m = br.readLine().split("<item>");
        br.close();
        in.close();
        String line;
        int a, b;
        for (int i = 1; i < m.length; i++) {
            a = m[i].indexOf("</link");
            line = withOutTag(m[i].substring(0, a));
            if (line.contains(site))
                line = line.substring(line.indexOf("info/") + 5);
            if (line.contains("#0"))
                line = line.replace("#0", "#2");
            b = m[i].indexOf("</title");
            bw.write(withOutTag(m[i].substring(a + 10, b))); //title
            bw.write(Const.N);
            bw.write(withOutTag(line)); //link
            bw.write(Const.N);
            if (SUMMARY)
                unread.addLink(line, now);
            a = m[i].indexOf("</des");
            bw.write(withOutTag(m[i].substring(b + 10, a))); //des
            bw.write(Const.N);
            b = m[i].indexOf("</a10");
            bw.write(DateHelper.parse(withOutTag(m[i].substring(a + 15, b)))
                    .getTimeInMills() + Const.N); //time
            bw.flush();
        }
        bw.close();
        unread.setBadge();
        unread.close();
    }
}
