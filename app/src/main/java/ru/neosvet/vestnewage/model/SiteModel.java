package ru.neosvet.vestnewage.model;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.workers.LoaderWorker;
import ru.neosvet.vestnewage.workers.SiteWorker;

public class SiteModel extends AndroidViewModel {
    public static final String TAG = "site";

    public SiteModel(@NonNull Application application) {
        super(application);
    }

    public void startLoad(String url, String file) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build();
        Data.Builder data = new Data.Builder()
                .putString(Const.TASK, this.getClass().getSimpleName())
                .putString(Const.LINK, url)
                .putString(Const.FILE, file);
        OneTimeWorkRequest task = new OneTimeWorkRequest
                .Builder(SiteWorker.class)
                .setInputData(data.build())
                .setConstraints(constraints)
                .build();
        WorkContinuation job = WorkManager.getInstance(getApplication())
                .beginUniqueWork(TAG, ExistingWorkPolicy.REPLACE, task);
        task = new OneTimeWorkRequest
                .Builder(LoaderWorker.class)
                .setInputData(data.build())
                .setConstraints(constraints)
                .addTag(TAG)
                .build();
        job = job.then(task);
        job.enqueue();
    }
}
