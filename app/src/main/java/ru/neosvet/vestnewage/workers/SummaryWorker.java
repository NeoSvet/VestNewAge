package ru.neosvet.vestnewage.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import ru.neosvet.utils.ErrorUtils;
import ru.neosvet.vestnewage.service.LoaderService;
import ru.neosvet.vestnewage.loader.SummaryLoader;

public class SummaryWorker extends Worker {
    public SummaryWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String error;
        ErrorUtils.setData(getInputData());
        try {
            SummaryLoader loader = new SummaryLoader();
            loader.loadList(false);
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            ErrorUtils.setError(e);
            error = e.getMessage();
        }
        LoaderService.postCommand(LoaderService.STOP, error);
        return Result.failure();
    }
}
