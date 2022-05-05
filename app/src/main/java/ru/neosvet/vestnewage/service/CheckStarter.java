package ru.neosvet.vestnewage.service;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class CheckStarter extends Worker {
    public static final String TAG_PERIODIC = "check periodic";

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
