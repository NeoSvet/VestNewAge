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
import ru.neosvet.vestnewage.workers.LoaderWorker;
import ru.neosvet.vestnewage.workers.SiteWorker;

public class SiteModel extends ProgressModel {
    public static final String TAG = "site";
    private static SiteModel current = null;

    public static SiteModel getInstance() {
        return current;
    }

    public SiteModel(@NonNull Application application) {
        super(application);
        work = WorkManager.getInstance();
        state = work.getWorkInfosByTagLiveData(TAG);
        inProgress = false;
        current = this;
    }

    public void startLoad(String url, String file) {
        inProgress = true;
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build();
        Data.Builder data = new Data.Builder()
                .putString(ProgressModel.NAME, this.getClass().getSimpleName())
                .putString(Const.LINK, url)
                .putString(Const.FILE, file);
        OneTimeWorkRequest task = new OneTimeWorkRequest
                .Builder(SiteWorker.class)
                .setInputData(data.build())
                .setConstraints(constraints)
                .addTag(TAG)
                .build();
        WorkContinuation job = work.beginUniqueWork(TAG,
                ExistingWorkPolicy.REPLACE, task);
        task = new OneTimeWorkRequest
                .Builder(LoaderWorker.class)
                .setInputData(data.build())
                .setConstraints(constraints)
                .build();
        job = job.then(task);
        job.enqueue();
    }
}
