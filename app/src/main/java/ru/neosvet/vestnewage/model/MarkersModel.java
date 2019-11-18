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
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.workers.MarkersWorker;

public class MarkersModel extends ProgressModel {
    public static final String TAG = "markers";
    private static MarkersModel current = null;

    public static MarkersModel getInstance() {
        return current;
    }

    public MarkersModel(@NonNull Application application) {
        super(application);
        current = this;
    }

    public void start(boolean export, String file) {
        ProgressHelper.setBusy(true);
        inProgress = true;
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(false)
                .build();
        Data data = new Data.Builder()
                .putBoolean(Const.MODE, export)
                .putString(Const.FILE, file)
                .build();
        OneTimeWorkRequest task = new OneTimeWorkRequest
                .Builder(MarkersWorker.class)
                .setConstraints(constraints)
                .setInputData(data)
                .build();
        WorkContinuation job = WorkManager.getInstance().beginUniqueWork(TAG,
                ExistingWorkPolicy.REPLACE, task);
        job.enqueue();
    }
}
