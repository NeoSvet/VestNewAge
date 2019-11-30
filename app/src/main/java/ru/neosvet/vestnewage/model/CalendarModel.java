package ru.neosvet.vestnewage.model;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.support.annotation.NonNull;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.workers.CalendarWolker;
import ru.neosvet.vestnewage.workers.LoaderWorker;

public class CalendarModel extends AndroidViewModel {
    public static final String TAG = "calendar";

    public CalendarModel(@NonNull Application application) {
        super(application);
    }

    public void startLoad(int month, int year, boolean updateUnread) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build();
        Data data = new Data.Builder()
                .putString(Const.TASK, this.getClass().getSimpleName())
                .putInt(Const.MONTH, month)
                .putInt(Const.YEAR, year)
                .putBoolean(Const.UNREAD, updateUnread)
                .build();
        OneTimeWorkRequest task = new OneTimeWorkRequest
                .Builder(CalendarWolker.class)
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
