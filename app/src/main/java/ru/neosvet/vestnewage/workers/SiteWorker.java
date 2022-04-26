package ru.neosvet.vestnewage.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import ru.neosvet.utils.ErrorUtils;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.NeoClient;
import ru.neosvet.vestnewage.service.LoaderService;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.loader.SiteLoader;
import ru.neosvet.vestnewage.model.SiteModel;

public class SiteWorker extends Worker {
    public SiteWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String error;
        ErrorUtils.setData(getInputData());
        try {
            //LoaderHelper
            if (!LoaderService.start)
                return Result.success();
            String[] url = new String[]{
                    NeoClient.SITE,
                    NeoClient.SITE + SiteModel.NOVOSTI,
            };
            String[] file = new String[]{
                    Lib.getFile(SiteModel.MAIN).toString(),
                    Lib.getFile(SiteModel.NEWS).toString()
            };
            SiteLoader loader;
            for (int i = 0; i < url.length && !ProgressHelper.isCancelled(); i++) {
                loader = new SiteLoader(file[i]);
                loader.load(url[i]);
                ProgressHelper.upProg();
            }
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