package ru.neosvet.vestnewage.model;

import android.app.Application;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

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
import ru.neosvet.vestnewage.workers.BookWorker;
import ru.neosvet.vestnewage.workers.CalendarWolker;
import ru.neosvet.vestnewage.workers.LoaderWorker;
import ru.neosvet.vestnewage.workers.SiteWorker;
import ru.neosvet.vestnewage.workers.SummaryWorker;

public class LoaderModel extends ProgressModel {
    public static final String TAG = "loader";
    public static final int ALL = -1, DOWNLOAD_ALL = 0, DOWNLOAD_ID = 1, DOWNLOAD_YEAR = 2,
            DOWNLOAD_PAGE = 3, DOWNLOAD_FILE = 4, DOWNLOAD_PAGE_WITH_STYLE = 5;
    private static LoaderModel current = null;
    private Context context;
    private Data.Builder data;
    private Constraints constraints;
    private WorkContinuation job = null;
    private String msg;
    private int prog = 0, max;

    public static LoaderModel getInstance() {
        return current;
    }

    public LoaderModel(@NonNull Application application) {
        super(application);
        work = WorkManager.getInstance();
        state = work.getWorkInfosByTagLiveData(TAG);
        inProgress = false;
        current = this;
        context = application.getBaseContext();
    }

    public void startLoad(int mode, @Nullable String request) {
        inProgress = true;
        constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build();
        data = new Data.Builder()
                .putString(ProgressModel.NAME, this.getClass().getSimpleName())
                .putInt(Const.MODE, mode);
        OneTimeWorkRequest task;
        if (mode >= DOWNLOAD_PAGE) { // also DOWNLOAD_FILE,  DOWNLOAD_PAGE_WITH_STYLE
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
            showDialog();
            startTimer();
            if (mode == DOWNLOAD_ALL) {
                refreshLists(ALL);
                downloadAll();
            } else if (mode == DOWNLOAD_ID) {
                int id = Integer.parseInt(request);
                refreshLists(id);
                data.putInt(Const.SELECT, id);
                task = new OneTimeWorkRequest
                        .Builder(LoaderWorker.class)
                        .setInputData(data.build())
                        .setConstraints(constraints)
                        .addTag(TAG)
                        .build();
                job = job.then(task);
            } else if (mode == DOWNLOAD_YEAR) { // refresh list for year
                data.putInt(Const.YEAR, Integer.parseInt(request));
                task = new OneTimeWorkRequest
                        .Builder(CalendarWolker.class)
                        .setInputData(data.build())
                        .setConstraints(constraints)
                        .addTag(TAG)
                        .build();
                job = work.beginUniqueWork(TAG,
                        ExistingWorkPolicy.REPLACE, task);
            }
            task = new OneTimeWorkRequest
                    .Builder(LoaderWorker.class)
                    .setInputData(data.build())
                    .setConstraints(constraints)
                    .addTag(TAG)
                    .build();
        }
        job.enqueue();
    }

    private void showDialog() {

    }

    private void downloadAll() {

    }

    private void refreshLists(int id) {
        msg = context.getResources().getString(R.string.download_list);
        // подсчёт количества списков:
        int k = 0;
        DateHelper d = DateHelper.initToday(context);
        if (id == ALL || id == R.id.nav_book) {
            k = (d.getYear() - 2016) * 12 + d.getMonth() - 1; //poems from 02.16
            k += 9; // poslaniya (01.16-09.16)
        }
        if (id == ALL) {
            k += (d.getYear() - 2016) * 12 + d.getMonth(); // calendar from 01.16
            k += 4; // main, news, media and rss
        } else if (id == R.id.nav_main) // main, news, media
            k = 3;
        setProgMax(k);
        OneTimeWorkRequest task;
        if (id == ALL) { //Summary
            task = new OneTimeWorkRequest
                    .Builder(SummaryWorker.class)
                    .setInputData(data.build())
                    .setConstraints(constraints)
                    .addTag(TAG)
                    .build();
            job = work.beginUniqueWork(TAG,
                    ExistingWorkPolicy.REPLACE, task);
        }
        if (id == ALL || id == R.id.nav_main) { //Site
            task = new OneTimeWorkRequest
                    .Builder(SiteWorker.class)
                    .setInputData(data.build())
                    .setConstraints(constraints)
                    .addTag(TAG)
                    .build();
            if (id == ALL)
                job = job.then(task);
            else
                job = work.beginUniqueWork(TAG,
                        ExistingWorkPolicy.REPLACE, task);
        }
        if (id == ALL) { // Calendar
            data = data.putInt(Const.YEAR, ALL);
            task = new OneTimeWorkRequest
                    .Builder(CalendarWolker.class)
                    .setInputData(data.build())
                    .setConstraints(constraints)
                    .addTag(TAG)
                    .build();
            job = job.then(task);
        }
        if (id == ALL || id == R.id.nav_book) { // Book
            task = new OneTimeWorkRequest
                    .Builder(BookWorker.class)
                    .setInputData(data.build())
                    .setConstraints(constraints)
                    .addTag(TAG)
                    .build();
            if (id == ALL)
                job = job.then(task);
            else
                job = work.beginUniqueWork(TAG,
                        ExistingWorkPolicy.REPLACE, task);
        }
    }

    private void startTimer() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    while (!inProgress && prog < max) {
                        Thread.sleep(DateHelper.SEC_IN_MILLS);
                        publishProgress();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void setProgMax(int max) {
        //tut
        this.max = max;
        prog = 0;
    }

    public void upProg() {
        prog++;
    }

    public static File getFileList(Context context) {
        return new File(context.getFilesDir() + File.separator + Const.LIST);
    }

    public static File getFile(Context context, String name) {
        return new File(context.getFilesDir() + name);
    }
}
