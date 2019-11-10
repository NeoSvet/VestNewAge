package ru.neosvet.vestnewage.model;

import android.app.Application;
import android.support.annotation.NonNull;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.ProgressModel;
import ru.neosvet.vestnewage.workers.AdsWorker;
import ru.neosvet.vestnewage.workers.CalendarWolker;
import ru.neosvet.vestnewage.workers.PromWorker;
import ru.neosvet.vestnewage.workers.SummaryWorker;

public class SlashModel extends ProgressModel {
    public static final String TAG = "slash";
    private static SlashModel current = null;
    public boolean non_start = true;

    public static SlashModel getInstance() {
        return current;
    }

    public SlashModel(@NonNull Application application) {
        super(application);
        current = this;
    }

    public void startLoad(boolean boolSummary, int month, int year) {
        inProgress = true;
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();
        Data data1 = new Data.Builder()
                .putString(ProgressModel.NAME, this.getClass().getSimpleName())
                .putBoolean(Const.TIME, true)
                .build();
        OneTimeWorkRequest task = new OneTimeWorkRequest //SynchronTime
                .Builder(PromWorker.class)
                .setConstraints(constraints)
                .setInputData(data1)
                .build();
        WorkContinuation job = WorkManager.getInstance().beginUniqueWork(TAG,
                ExistingWorkPolicy.REPLACE, task);
        task = new OneTimeWorkRequest
                .Builder(AdsWorker.class)
                .setConstraints(constraints)
                .build();
        job = job.then(task);
        Data.Builder data2 = new Data.Builder()
                .putString(ProgressModel.NAME, this.getClass().getSimpleName());
        if (boolSummary) {
            task = new OneTimeWorkRequest
                    .Builder(SummaryWorker.class)
                    .setInputData(data2.build())
                    .setConstraints(constraints)
                    .addTag(TAG)
                    .build();
        } else {
            data2.putInt(Const.MONTH, month)
                    .putInt(Const.YEAR, year)
                    .putBoolean(Const.UNREAD, true);
            task = new OneTimeWorkRequest
                    .Builder(CalendarWolker.class)
                    .setInputData(data2.build())
                    .setConstraints(constraints)
                    .addTag(TAG)
                    .build();
        }
        job = job.then(task);
        job.enqueue();
    }
}
