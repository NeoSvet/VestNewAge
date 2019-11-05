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

import ru.neosvet.utils.ProgressModel;
import ru.neosvet.vestnewage.workers.SummaryWorker;

public class SummaryModel extends ProgressModel {
    public static final String TAG = "summary";
    private static SummaryModel current = null;

    public static SummaryModel getInstance() {
        return current;
    }

    public SummaryModel(@NonNull Application application) {
        super(application);
        work = WorkManager.getInstance();
        state = work.getWorkInfosByTagLiveData(TAG);
        inProgress = false;
        current = this;
    }

    public void startLoad() {
        inProgress = true;
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build();
        Data.Builder data = new Data.Builder()
                .putString(ProgressModel.NAME, this.getClass().getSimpleName());
        OneTimeWorkRequest task = new OneTimeWorkRequest
                .Builder(SummaryWorker.class)
                .setInputData(data.build())
                .setConstraints(constraints)
                .addTag(TAG)
                .build();
        WorkContinuation job = work.beginUniqueWork(TAG,
                ExistingWorkPolicy.REPLACE, task);
        job.enqueue();
    }
}
