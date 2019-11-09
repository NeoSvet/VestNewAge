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
import ru.neosvet.utils.ProgressModel;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.NotificationHelper;
import ru.neosvet.vestnewage.helpers.SummaryHelper;
import ru.neosvet.vestnewage.model.LoaderModel;
import ru.neosvet.vestnewage.model.SummaryModel;

public class SummaryWorker extends Worker {
    private Context context;
    private ProgressModel model;

    public SummaryWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
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
        String err, name;
        name = getInputData().getString(ProgressModel.NAME);
        model = ProgressModel.getModelByName(name);
        try {
            loadList();
            if (name.equals(SummaryModel.class.getSimpleName())) {
                SummaryHelper summaryHelper = new SummaryHelper(context);
                summaryHelper.updateBook();
            }
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            err = e.getMessage();
            Lib.LOG("SummaryWorker error: " + err);
        }
        return Result.failure(new Data.Builder()
                .putString(Const.ERROR, err)
                .build());
    }

    private String withOutTag(String s) {
        int i = s.indexOf(">") + 1;
        s = s.substring(i, s.indexOf("<", i));
        return s;
    }

    private void loadList() throws Exception {
        NotificationHelper notifHelper = new NotificationHelper(context);
        notifHelper.cancel(NotificationHelper.NOTIF_SUMMARY);
        String line;
        Lib lib = new Lib(context);
        InputStream in = new BufferedInputStream(lib.getStream(Const.SITE + "rss/?" + System.currentTimeMillis()));
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        BufferedWriter bw = new BufferedWriter(new FileWriter(context.getFilesDir() + Const.RSS));
        while ((line = br.readLine()) != null && !isCancelled()) {
            if (line.contains("</channel>")) break;
            if (line.contains("<item>")) {
                bw.write(withOutTag(br.readLine())); //title
                bw.write(Const.N);
                line = withOutTag(br.readLine()); //link
                if (line.contains(Const.SITE2))
                    line = line.substring(Const.SITE2.length());
                else if (line.contains(Const.SITE))
                    line = line.substring(Const.SITE.length());
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
    }

    public static int getListLink(Context context) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(context.getFilesDir() + Const.RSS));
        File file = LoaderModel.getFileList(context);
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
