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
import ru.neosvet.vestnewage.workers.CalendarWolker;
import ru.neosvet.vestnewage.workers.LoaderWorker;

public class CalendarModel extends ProgressModel {
    public static final String TAG = "calendar";
    private static CalendarModel current = null;

    public static CalendarModel getInstance() {
        return current;
    }

    public CalendarModel(@NonNull Application application) {
        super(application);
        current = this;
    }

    public void startLoad(int month, int year, boolean updateUnread) {
        ProgressHelper.getInstance().setBusy(true);
        inProgress = true;
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build();
        Data.Builder data = new Data.Builder()
                .putString(ProgressModel.NAME, this.getClass().getSimpleName())
                .putInt(Const.MONTH, month)
                .putInt(Const.YEAR, year)
                .putBoolean(Const.UNREAD, updateUnread);
        OneTimeWorkRequest task = new OneTimeWorkRequest
                .Builder(CalendarWolker.class)
                .setInputData(data.build())
                .setConstraints(constraints)
                .addTag(TAG)
                .build();
        WorkContinuation job = WorkManager.getInstance().beginUniqueWork(TAG,
                ExistingWorkPolicy.REPLACE, task);
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
