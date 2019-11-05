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
import ru.neosvet.vestnewage.workers.CalendarWolker;

public class CalendarModel extends ProgressModel {
    public static final String TAG = "calendar";
    private static CalendarModel current = null;
    public boolean loadList = true;

    public static CalendarModel getInstance() {
        return current;
    }

    public CalendarModel(@NonNull Application application) {
        super(application);
        work = WorkManager.getInstance();
        state = work.getWorkInfosByTagLiveData(TAG);
        inProgress = false;
        current = this;
    }

    public void startLoad(int month, int year, boolean updateUnread) {
        inProgress = true;
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build();
        Data.Builder data = new Data.Builder()
                .putString(ProgressModel.NAME, this.getClass().getSimpleName())
                .putInt(CalendarWolker.MONTH, month)
                .putInt(CalendarWolker.YEAR, year)
                .putBoolean(CalendarWolker.UNREAD, updateUnread);
        OneTimeWorkRequest task = new OneTimeWorkRequest
                .Builder(CalendarWolker.class)
                .setInputData(data.build())
                .setConstraints(constraints)
                .addTag(TAG)
                .build();
        WorkContinuation job = work.beginUniqueWork(TAG,
                ExistingWorkPolicy.REPLACE, task);
        job.enqueue();
    }
}
