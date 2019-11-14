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
import ru.neosvet.vestnewage.workers.BaseWorker;

public class BaseModel extends ProgressModel {
    public static final String TAG = "base";
    private static BaseModel current = null;

    public static BaseModel getInstance() {
        return current;
    }

    public BaseModel(@NonNull Application application) {
        super(application);
        current = this;
    }

    public void startClear(String[] request) {
        inProgress = true;
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(false)
                .build();
        Data data = new Data.Builder()
                .putStringArray(Const.MSG, request)
                .build();
        OneTimeWorkRequest task = new OneTimeWorkRequest
                .Builder(BaseWorker.class)
                .setConstraints(constraints)
                .setInputData(data)
                .build();
        WorkContinuation job = WorkManager.getInstance().beginUniqueWork(TAG,
                ExistingWorkPolicy.REPLACE, task);
        job.enqueue();
    }
}
