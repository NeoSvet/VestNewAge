package ru.neosvet.vestnewage.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.ErrorUtils;
import ru.neosvet.vestnewage.App;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.service.LoaderService;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.loader.CalendarLoader;
import ru.neosvet.vestnewage.model.CalendarModel;

public class CalendarWorker extends Worker {

    public CalendarWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        boolean CALENDAR = getInputData().getString(Const.TASK).equals(CalendarModel.TAG);
        String error;
        ErrorUtils.setData(getInputData());
        try {
            CalendarLoader loader = new CalendarLoader();
            if (CALENDAR) {
                ProgressHelper.setBusy(true);
                ProgressHelper.postProgress(new Data.Builder()
                        .putBoolean(Const.START, true)
                        .build());
                loader.setDate(getInputData().getInt(Const.YEAR, 0),
                        getInputData().getInt(Const.MONTH, 0));
                loader.loadListMonth(getInputData().getBoolean(Const.UNREAD, false));
                ProgressHelper.postProgress(new Data.Builder()
                        .putBoolean(Const.LIST, true)
                        .build());
                return Result.success();
            }
            //LoaderHelper
            if (!LoaderService.start)
                return Result.success();
            DateHelper d = DateHelper.initToday();
            if (getInputData().getInt(Const.MODE, 0) == LoaderService.DOWNLOAD_YEAR) {
                ProgressHelper.setMessage(App.context.getString(R.string.download_list));
                int m, y = getInputData().getInt(Const.YEAR, 0);
                if (d.getYear() != y)
                    m = 12;
                else
                    m = d.getMonth();
                ProgressHelper.setMax(m);
                loader.loadListYear(y, m + 1);
            } else { //all calendar
                int max_y = d.getYear() + 1, max_m = 13;
                for (int y = 2016; y < max_y && LoaderService.start; y++) {
                    if (y == d.getYear())
                        max_m = d.getMonth() + 1;
                    loader.loadListYear(y, max_m);
                }
            }
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            ErrorUtils.setError(e);
            error = e.getMessage();
        }
        if (CALENDAR) {
            ProgressHelper.postProgress(new Data.Builder()
                    .putBoolean(Const.FINISH, true)
                    .putString(Const.ERROR, error)
                    .build());
        } else {
            LoaderService.postCommand(LoaderService.STOP, error);
            return Result.failure();
        }
        return Result.failure();
    }


}
