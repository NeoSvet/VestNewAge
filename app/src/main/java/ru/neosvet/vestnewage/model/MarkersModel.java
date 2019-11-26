package ru.neosvet.vestnewage.model;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.MutableLiveData;
import android.support.annotation.NonNull;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.workers.MarkersWorker;

public class MarkersModel extends AndroidViewModel {
    public static final String TAG = "markers";
    public static MutableLiveData<Data> live = new MutableLiveData<Data>();
    public boolean inProgress;

    public MarkersModel(@NonNull Application application) {
        super(application);
    }

    public void start(boolean export, String file) {
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
