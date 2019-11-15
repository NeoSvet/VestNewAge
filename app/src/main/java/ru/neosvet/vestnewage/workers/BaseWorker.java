package ru.neosvet.vestnewage.workers;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
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
                        /*dataBase = new DataBase(context, d.getMY());
                        db = dataBase.getWritableDatabase();
                        db.delete(Const.TITLE, null, null);
                        db.delete(DataBase.PARAGRAPH, null, null);
                        db.close();
                        dataBase.close();*/
                        d.changeMonth(1);
                    }
                } else if (request[i].equals(DataBase.MARKERS)) {
                    DataBase dataBase = new DataBase(context, request[i]);
                    SQLiteDatabase db = dataBase.getWritableDatabase();
                    db.delete(request[i], null, null);
                    db.delete(DataBase.COLLECTIONS, null, null);
                    ContentValues cv = new ContentValues();
                    cv.put(Const.TITLE, context.getResources().getString(R.string.no_collections));
                    db.insert(DataBase.COLLECTIONS, null, cv);
                    db.close();
                    dataBase.close();
                } else {//materials
                    f = new File(path + request[i]);
                    f.delete();
                  //db.delete(Const.TITLE, null, null);
                  //db.delete(DataBase.PARAGRAPH, null, null);
                }
            }
            BaseModel.getInstance().postProgress(new Data.Builder()
                    .putBoolean(Const.FINISH, true)
                    .build());
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            error = e.getMessage();
            Lib.LOG("BaseWorker error: " + error);
        }
        BaseModel.getInstance().postProgress(new Data.Builder()
                .putBoolean(Const.FINISH, true)
                .putString(Const.ERROR, error)
                .build());
        return Result.failure();
    }
}
