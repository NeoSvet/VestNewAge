package ru.neosvet.vestnewage.workers;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;

import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.fragment.BookFragment;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;

public class BaseWorker extends Worker {
    private final Context context;
    private long size = 0;

    public BaseWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        ProgressHelper.setBusy(true);
        String error;
        try {
            String[] request = getInputData().getStringArray(Const.MSG);
            File f;
            String path = context.getFilesDir().getParent() + "/databases/";
            for (String r : request) {
                if (r.equals(Const.START) || r.equals(Const.END)) { //book
                    DateHelper d;
                    int max_y, max_m;
                    if (r.equals(Const.START)) { //book prev years
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
                        if (f.exists()) {
                            size += f.length();
                            f.delete();
                            f = new File(path + d.getMY() + "-journal");
                            if (f.exists()) {
                                size += f.length();
                                f.delete();
                            }
                        }
                        d.changeMonth(1);
                    }
                } else if (r.equals(Const.FILE)) { //cache
                    clearFolder(new File(context.getFilesDir().getParent() + "/cache/"));
                } else {//markers or materials
                    f = new File(path + r);
                    if (f.exists()) {
                        size += f.length();
                        f.delete();
                    }
                }
            }
            ProgressHelper.postProgress(new Data.Builder()
                    .putBoolean(Const.FINISH, true)
                    .putLong(Const.PROG, size)
                    .build());
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            error = e.getMessage();
        }
        ProgressHelper.postProgress(new Data.Builder()
                .putBoolean(Const.FINISH, true)
                .putString(Const.ERROR, error)
                .build());
        return Result.failure();
    }

    private void clearFolder(File folder) throws Exception {
        for (File f : folder.listFiles()) {
            if (f.isFile()) {
                size += f.length();
                f.delete();
            } else {
                clearFolder(f);
                f.delete();
            }
        }
    }

}
