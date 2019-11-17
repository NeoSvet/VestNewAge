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
import ru.neosvet.vestnewage.workers.SlashWorker;
import ru.neosvet.vestnewage.workers.SummaryWorker;

public class SlashModel extends ProgressModel {
    public static final String TAG = "slash";
    private static SlashModel current = null;
    public boolean non_start = true;

    public static SlashModel getInstance() {
        return current;
    }

    public SlashModel(@NonNull Application application) {
        super(application);
        current = this;
    }

    public void startLoad(boolean boolSummary, int month, int year) {
        ProgressHelper.getInstance().setBusy(true);
        inProgress = true;
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();
        OneTimeWorkRequest task = new OneTimeWorkRequest
                .Builder(SlashWorker.class)
                .setConstraints(constraints)
                .build();
        WorkContinuation job = WorkManager.getInstance().beginUniqueWork(TAG,
                ExistingWorkPolicy.REPLACE, task);
        Data.Builder data = new Data.Builder()
                .putString(ProgressModel.NAME, this.getClass().getSimpleName());
        if (boolSummary) {
            task = new OneTimeWorkRequest
                    .Builder(SummaryWorker.class)
                    .setInputData(data.build())
                    .setConstraints(constraints)
                    .addTag(TAG)
                    .build();
        } else {
            data.putInt(Const.MONTH, month)
                    .putInt(Const.YEAR, year)
                    .putBoolean(Const.UNREAD, true);
            task = new OneTimeWorkRequest
                    .Builder(CalendarWolker.class)
                    .setInputData(data.build())
                    .setConstraints(constraints)
                    .addTag(TAG)
                    .build();
        }
        job = job.then(task);
        job.enqueue();
    }
}
