package ru.neosvet.vestnewage.service;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.App;

public class CheckStarter extends Worker {
    private static final String TAG_PERIODIC = "check periodic";

    public static void set(int time) {
        WorkManager work = WorkManager.getInstance(App.context);
        work.cancelAllWorkByTag(TAG_PERIODIC);
        if (time == Const.TURN_OFF) return;
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();
        PeriodicWorkRequest task = new PeriodicWorkRequest
                .Builder(CheckStarter.class, time, TimeUnit.MINUTES, time - 5, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag(TAG_PERIODIC)
                .build();
        work.enqueue(task);
    }

    public CheckStarter(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        CheckService.postCommand(true);
        return Result.success();
    }
}
