package ru.neosvet.vestnewage.model;

import android.app.Application;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.ProgressModel;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.workers.BookWorker;
import ru.neosvet.vestnewage.workers.CalendarWolker;
import ru.neosvet.vestnewage.workers.LoaderWorker;
import ru.neosvet.vestnewage.workers.PageWorker;
import ru.neosvet.vestnewage.workers.SiteWorker;
import ru.neosvet.vestnewage.workers.SummaryWorker;

public class LoaderModel extends ProgressModel {
    public static final String TAG = "loader";
    public static final byte DOWNLOAD_ALL = 0, DOWNLOAD_YEAR = 1, DOWNLOAD_ID = 2,
            DOWNLOAD_PAGE = 3, DOWNLOAD_FILE = 4, DOWNLOAD_PAGE_WITH_STYLE = 5;
    private static LoaderModel current = null;

    public static LoaderModel getInstance() {
        return current;
    }

    public LoaderModel(@NonNull Application application) {
        super(application);
        work = WorkManager.getInstance();
        state = work.getWorkInfosByTagLiveData(TAG);
        inProgress = false;
        current = this;
    }

    public void startLoad(byte mode, @Nullable Data request) {
        inProgress = true;
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build();
        Data.Builder data = new Data.Builder()
                .putString(ProgressModel.NAME, this.getClass().getSimpleName());
        OneTimeWorkRequest task;
        if (mode >= DOWNLOAD_PAGE) {
            data.putString(DataBase.LINK, request.getString(DataBase.LINK));
            task = new OneTimeWorkRequest
                    .Builder(PageWorker.class)
                    .setInputData(data.build())
                    .setConstraints(constraints)
                    .addTag(TAG)
                    .build();
            WorkContinuation job = work.beginUniqueWork(TAG,
                    ExistingWorkPolicy.REPLACE, task);
            job.enqueue();
        } else {
            data.putInt(Const.MODE, mode);
            if (mode == DOWNLOAD_ID) {
                WorkContinuation job = null;
                int id = request.getInt(Const.SELECT, 0);
                if (id == -1) { //Summary
                    task = new OneTimeWorkRequest
                            .Builder(SummaryWorker.class)
                            .setInputData(data.build())
                            .setConstraints(constraints)
                            .addTag(TAG)
                            .build();
                    job = work.beginUniqueWork(TAG,
                            ExistingWorkPolicy.REPLACE, task);
                }
                if (id == -1 || id == R.id.nav_main) { //Site
                    task = new OneTimeWorkRequest
                            .Builder(SiteWorker.class)
                            .setInputData(data.build())
                            .setConstraints(constraints)
                            .addTag(TAG)
                            .build();
                    if (id == -1)
                        job = job.then(task);
                    else
                        job = work.beginUniqueWork(TAG,
                                ExistingWorkPolicy.REPLACE, task);
                }
                if (id == -1) { // Calendar
                    task = new OneTimeWorkRequest
                            .Builder(CalendarWolker.class)
                            .setInputData(data.build())
                            .setConstraints(constraints)
                            .addTag(TAG)
                            .build();
                    job = job.then(task);
                }
                if (id == -1 || id == R.id.nav_book) { // Book
                    task = new OneTimeWorkRequest
                            .Builder(BookWorker.class)
                            .setInputData(data.build())
                            .setConstraints(constraints)
                            .addTag(TAG)
                            .build();
                    if (id == -1)
                        job = job.then(task);
                    else
                        job = work.beginUniqueWork(TAG,
                                ExistingWorkPolicy.REPLACE, task);
                }
                if (job != null)
                    job.enqueue();
            } else if (mode == DOWNLOAD_YEAR) {
                data.putInt(Const.YEAR, request.getInt(Const.YEAR, 0));
            }
            task = new OneTimeWorkRequest
                    .Builder(LoaderWorker.class)
                    .setInputData(data.build())
                    .setConstraints(constraints)
                    .addTag(TAG)
                    .build();
        }
    }
}
