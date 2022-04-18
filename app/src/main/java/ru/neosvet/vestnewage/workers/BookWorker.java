package ru.neosvet.vestnewage.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.ErrorUtils;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.LoaderHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.loader.BookLoader;

public class BookWorker extends Worker implements BookLoader.Handler {
    public BookWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String error;
        ErrorUtils.setData(getInputData());
        try {
            BookLoader loader = new BookLoader(this);
            if (getInputData().getBoolean(Const.OTKR, false)) { //загрузка Посланий за 2004-2015
                loader.loadOtrk();
                return Result.success();
            }
            //LoaderHelper
            loader.loadTolkovaniya();
            loader.loadPoems(false);
            if (!LoaderHelper.start)
                return Result.success();
            DateHelper d = DateHelper.initToday();
            ProgressHelper.setMax((d.getYear() - 2016) * 12);
            loader.loadAllUcoz();
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            ErrorUtils.setError(e);
            error = e.getMessage();
        }
        LoaderHelper.postCommand(LoaderHelper.STOP_WITH_NOTIF, error);
        return Result.failure();
    }

    @Override
    public void setMax(int value) {
        ProgressHelper.setMax(value);
    }

    @Override
    public void upProg() {
        ProgressHelper.upProg();
    }

    @Override
    public void postMessage(@NonNull String value) {
        ProgressHelper.setMessage(value);
    }
}
