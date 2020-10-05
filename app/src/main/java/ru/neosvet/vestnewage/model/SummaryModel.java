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
import ru.neosvet.vestnewage.workers.SummaryWorker;

public class SummaryModel extends AndroidViewModel {
    public static final String TAG = "summary";

    public SummaryModel(@NonNull Application application) {
        super(application);
    }

    public void startLoad() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build();
        Data data = new Data.Builder()
                .putString(Const.TASK, this.getClass().getSimpleName())
                .build();
        OneTimeWorkRequest task = new OneTimeWorkRequest
                .Builder(SummaryWorker.class)
                .setInputData(data)
                .setConstraints(constraints)
                .addTag(TAG)
                .build();
        WorkContinuation job = WorkManager.getInstance().beginUniqueWork(TAG,
                ExistingWorkPolicy.REPLACE, task);
        if (!LoaderModel.inProgress) {
            task = new OneTimeWorkRequest
                    .Builder(LoaderWorker.class)
                    .setInputData(data)
                    .setConstraints(constraints)
                    .addTag(TAG)
                    .build();
            job = job.then(task);
        }
        job.enqueue();
    }
}
