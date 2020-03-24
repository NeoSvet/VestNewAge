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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.LoaderHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.helpers.SummaryHelper;
import ru.neosvet.vestnewage.helpers.UnreadHelper;
import ru.neosvet.vestnewage.model.SummaryModel;

public class SummaryWorker extends Worker {
    private Context context;

    public SummaryWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        boolean SUMMARY = getInputData().getString(Const.TASK).equals(SummaryModel.class.getSimpleName());
        String error;
        try {
            if (SUMMARY) {
                ProgressHelper.setBusy(true);
                ProgressHelper.postProgress(new Data.Builder()
                        .putBoolean(Const.START, true)
                        .build());
                loadList();
                SummaryHelper summaryHelper = new SummaryHelper(context);
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
            error = e.getMessage();
        }
        if (SUMMARY) {
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

    private String withOutTag(String s) {
        int i = s.indexOf(">") + 1;
        s = s.substring(i, s.indexOf("<", i));
        return s;
    }

    private void loadList() throws Exception {
        String line;
        Lib lib = new Lib(context);
        InputStream in = new BufferedInputStream(lib.getStream(Const.SITE + "rss/?" + System.currentTimeMillis()));
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        BufferedWriter bw = new BufferedWriter(new FileWriter(context.getFilesDir() + Const.RSS));
        DateHelper now = DateHelper.initNow(context);
        UnreadHelper unread = new UnreadHelper(context);
        while ((line = br.readLine()) != null) {
            if (line.contains("</channel>")) break;
            if (line.contains("<item>")) {
                bw.write(withOutTag(br.readLine())); //title
                bw.write(Const.N);
                line = withOutTag(br.readLine()); //link
                if (line.contains(Const.SITE))
                    line = line.substring(Const.SITE.length());
                unread.addLink(line, now);
                bw.write(line);
                bw.write(Const.N);
                bw.write(withOutTag(br.readLine())); //des
                bw.write(Const.N);
                bw.write(DateHelper.parse(context, withOutTag(br.readLine())).getTimeInMills() + Const.N); //time
                bw.flush();
            }
        }
        bw.close();
        br.close();
        in.close();
        unread.setBadge();
        unread.close();
    }

    public static int getListLink(Context context) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(context.getFilesDir() + Const.RSS));
        File file = LoaderHelper.getFileList(context);
        BufferedWriter bw = new BufferedWriter(new FileWriter(file));
        String s;
        int k = 0;
        while (br.readLine() != null) { //title
            s = br.readLine(); //link
            bw.write(s);
            bw.newLine();
            bw.flush();
            k++;
            br.readLine(); //des
            br.readLine(); //time
        }
        bw.close();
        br.close();
        return k;
    }
}
