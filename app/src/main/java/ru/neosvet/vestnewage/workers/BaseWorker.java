package ru.neosvet.vestnewage.workers;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;

import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.fragment.BookFragment;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.model.BaseModel;

public class BaseWorker extends Worker {
    private Context context;

    public BaseWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        String error;
        try {
            String[] request = getInputData().getStringArray(Const.MSG);
            File f;
            String path = context.getFilesDir().getParent() + "/databases/";
            for (int i = 0; i < request.length; i++) {
                if (request[i].equals(Const.START) || request[i].equals(Const.END)) { //book
                    DateHelper d;
                    int max_y, max_m;
                    if (request[i].equals(Const.START)) { //book prev years
                        d = DateHelper.initToday(context);
                        max_y = d.getYear() - 1;
                        max_m = 12;
                        d = DateHelper.putYearMonth(context, 2004, 8);
                        SharedPreferences pref = context.getSharedPreferences(BookFragment.class.getSimpleName(), Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = pref.edit();
                        editor.putBoolean(Const.OTKR, false);
                        editor.apply();
                    } else { //book cur year
                        d = DateHelper.initToday(context);
                        max_y = d.getYear();
                        max_m = d.getMonth();
                        d = DateHelper.putYearMonth(context, max_y, 1);
                    }
                    while (d.getYear() < max_y || (d.getYear() == max_y && d.getMonth() <= max_m)) {
                        f = new File(path + d.getMY());
                        if (f.exists())
                            f.delete();
                        d.changeMonth(1);
                    }
                } else {//markers or materials
                    f = new File(path + request[i]);
                    f.delete();
                }
            }
            BaseModel.live.postValue(new Data.Builder()
                    .putBoolean(Const.FINISH, true)
                    .build());
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            error = e.getMessage();
            Lib.LOG("BaseWorker error: " + error);
        }
        BaseModel.live.postValue(new Data.Builder()
                .putBoolean(Const.FINISH, true)
                .putString(Const.ERROR, error)
                .build());
        return Result.failure();
    }
}
