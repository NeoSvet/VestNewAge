package ru.neosvet.vestnewage.workers;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
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
            String request = getInputData().getString(Const.MSG);
            DataBase dataBase = null;
            SQLiteDatabase db = null;
            Cursor cursor = null;
            if (request.contains(DataBase.LIKE)) { //book
                DateHelper d;
                int max_y, max_m;
                if (request.contains("NOT")) {
                    d = DateHelper.putYearMonth(context, 2004, 8);
                    max_y = 2016;
                    max_m = 9;
                } else {
                    d = DateHelper.initToday(context);
                    max_y = d.getYear();
                    max_m = d.getMonth();
                    d = DateHelper.putYearMonth(context, 2016, 1);
                }
                DataBase dbPar;
                Cursor curPar;
                int id;
                while (!(d.getYear() == max_y && d.getMonth() > max_m)) {
                    dataBase = new DataBase(context, d.getMY());
                    db = dataBase.getWritableDatabase();
                    cursor = db.query(Const.TITLE, null, request, new String[]
                            {"%" + Const.POEMS + "%"}, null, null, Const.LINK);
                    if (cursor.moveToFirst()) {
//                        id = cursor.getInt(cursor.getColumnIndex(DataBase.ID));
//                        dbPar = new DataBase(context, link);
//                        db2 = dbPar.getWritableDatabase();
//                        db2.delete()
                    }
                    cursor.close();
                    db.close();
                    dataBase.close();
                    d.changeMonth(1);
                }
            } else {
                dataBase = new DataBase(context, request);
                db = dataBase.getWritableDatabase();
                if (request.equals(DataBase.MARKERS))
                    db.delete(request, null, null);
                else //materials
                    db.delete(request, null, null);
                cursor.close();
                db.close();
                dataBase.close();
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
