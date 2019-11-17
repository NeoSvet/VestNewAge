package ru.neosvet.vestnewage.model;

import android.app.Application;
import android.content.Context;
import android.support.annotation.NonNull;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import java.io.File;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.ProgressModel;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.workers.BookWorker;
import ru.neosvet.vestnewage.workers.CalendarWolker;
import ru.neosvet.vestnewage.workers.LoaderWorker;
import ru.neosvet.vestnewage.workers.SiteWorker;
import ru.neosvet.vestnewage.workers.SummaryWorker;

public class LoaderModel extends ProgressModel {
    public static final String TAG = "loader";
    public static final int ALL = -1, DOWNLOAD_ALL = 0, DOWNLOAD_ID = 1, DOWNLOAD_YEAR = 2,
            DOWNLOAD_PAGE = 3, DOWNLOAD_PAGE_WITH_STYLE = 4;
    public static final int DIALOG_SHOW = 0, DIALOG_MSG = 1;
    private static LoaderModel current = null;
    private Data.Builder data;
    private Constraints constraints;
    private WorkManager work;

    public static LoaderModel getInstance() {
        return current;
    }

    public LoaderModel(@NonNull Application application) {
        super(application);
        work = WorkManager.getInstance();
        current = this;
    }

    public void startLoad(int mode, String request) {
        ProgressHelper.getInstance().setBusy(true);
        inProgress = true;
        constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build();
        data = new Data.Builder()
                .putString(ProgressModel.NAME, this.getClass().getSimpleName())
                .putInt(Const.MODE, mode);
        OneTimeWorkRequest task;
        WorkContinuation job;
        if (mode >= DOWNLOAD_PAGE) { // also DOWNLOAD_FILE, DOWNLOAD_PAGE_WITH_STYLE
            data.putString(Const.LINK, request);
            task = new OneTimeWorkRequest
                    .Builder(LoaderWorker.class)
                    .setInputData(data.build())
                    .setConstraints(constraints)
                    .addTag(TAG)
                    .build();
            job = work.beginUniqueWork(TAG,
                    ExistingWorkPolicy.REPLACE, task);
        } else {
            if (mode == DOWNLOAD_ALL) {
                job = refreshLists(ALL);
            } else if (mode == DOWNLOAD_ID) {
                int id = Integer.parseInt(request);
                job = refreshLists(id);
                data.putInt(Const.SELECT, id);
            } else if (mode == DOWNLOAD_YEAR) { // refresh list for year
                data.putInt(Const.YEAR, Integer.parseInt(request));
                task = new OneTimeWorkRequest
                        .Builder(CalendarWolker.class)
                        .setInputData(data.build())
                        .setConstraints(constraints)
                        .addTag(CalendarModel.TAG)
                        .build();
                job = work.beginUniqueWork(TAG,
                        ExistingWorkPolicy.REPLACE, task);
            } else
                return;
            task = new OneTimeWorkRequest
                    .Builder(LoaderWorker.class)
                    .setInputData(data.build())
                    .setConstraints(constraints)
                    .addTag(TAG)
                    .build();
            job = job.then(task);
        }
        job.enqueue();
    }

    private WorkContinuation refreshLists(int id) {
        // подсчёт количества списков:
        int k = 0;
        DateHelper d = null;
        if (id == ALL || id == R.id.nav_book) {
            d = DateHelper.initToday(getApplication().getBaseContext());
            k = (d.getYear() - 2016) * 12 + d.getMonth() - 1; //poems from 02.16
            k += 9; // poslaniya (01.16-09.16)
        }
        if (id == ALL) {
            k += (d.getYear() - 2016) * 12 + d.getMonth(); //calendar from 01.16
            k += 4; //main, news, media and rss
        } else if (id == R.id.nav_main) //main, news
            k = 2;
        ProgressHelper.getInstance().setMessage(getApplication().getBaseContext().getResources().getString(R.string.download_list));
        ProgressHelper.getInstance().setMax(k);
        postProgress(new Data.Builder().putInt(Const.DIALOG, LoaderModel.DIALOG_SHOW).build());
        OneTimeWorkRequest task;
        WorkContinuation job = null;
        if (id == ALL) { //Summary
            task = new OneTimeWorkRequest
                    .Builder(SummaryWorker.class)
                    .setInputData(data.build())
                    .setConstraints(constraints)
                    .addTag(SummaryModel.TAG)
                    .build();
            job = work.beginUniqueWork(TAG,
                    ExistingWorkPolicy.REPLACE, task);
        }
        if (id == ALL || id == R.id.nav_main) { //Site
            task = new OneTimeWorkRequest
                    .Builder(SiteWorker.class)
                    .setInputData(data.build())
                    .setConstraints(constraints)
                    .addTag(SiteModel.TAG)
                    .build();
            if (id == ALL)
                job = job.then(task);
            else
                job = work.beginUniqueWork(TAG,
                        ExistingWorkPolicy.REPLACE, task);
        }
        if (id == ALL) { //Calendar
            data = data.putInt(Const.YEAR, ALL);
            task = new OneTimeWorkRequest
                    .Builder(CalendarWolker.class)
                    .setInputData(data.build())
                    .setConstraints(constraints)
                    .addTag(CalendarModel.TAG)
                    .build();
            job = job.then(task);
        }
        if (id == ALL || id == R.id.nav_book) { //Book
            task = new OneTimeWorkRequest
                    .Builder(BookWorker.class)
                    .setInputData(data.build())
                    .setConstraints(constraints)
                    .addTag(BookModel.TAG)
                    .build();
            if (id == ALL)
                job = job.then(task);
            else
                job = work.beginUniqueWork(TAG,
                        ExistingWorkPolicy.REPLACE, task);
        }
        return job;
    }

    public String initMsg(String s) {
        if (s.contains("/")) {
            s = s.substring(s.lastIndexOf("/") + 1, s.lastIndexOf("."));
            if (s.contains("_"))
                s = s.substring(0, s.length() - 2);
            DateHelper d = DateHelper.parse(getApplication().getBaseContext(), s);
            return d.getMonthString() + " " + d.getYear();
        }
        return s;
    }

    public static File getFileList(Context context) {
        return new File(context.getFilesDir() + File.separator + Const.LIST);
    }
}
