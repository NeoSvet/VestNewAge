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
import ru.neosvet.vestnewage.workers.BaseWorker;

public class BaseModel extends AndroidViewModel {
    public static final String TAG = "base";
    public static MutableLiveData<Data> live = new MutableLiveData<Data>();
    public boolean inProgress;

    public BaseModel(@NonNull Application application) {
        super(application);
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
