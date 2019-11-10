package ru.neosvet.vestnewage.workers;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;

import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.PromHelper;
import ru.neosvet.vestnewage.model.SlashModel;

public class PromWorker extends Worker {
    private Context context;
    public static final String TAG = "Prom";

    public PromWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        if (getInputData().getBoolean(Const.TIME, false)) {
            return startSynchronTime();
        }
        PromHelper prom = new PromHelper(context, null);
        prom.showNotif();
        SharedPreferences pref = context.getSharedPreferences(Const.PROM, Context.MODE_PRIVATE);
        int p = pref.getInt(Const.TIME, Const.TURN_OFF);
        if (p != Const.TURN_OFF)
            prom.initWorker(p);
        return Result.success();
    }

    private Result startSynchronTime() {
        String err;
        try {
            synchronTime();
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            Lib.LOG("PromWorker error: " + e.getMessage());
        }
        return Result.failure();
    }

    private void synchronTime() throws Exception {
        Request.Builder builderRequest = new Request.Builder();
        builderRequest.url(Const.SITE2);
        builderRequest.header(Const.USER_AGENT, context.getPackageName());
        OkHttpClient client = Lib.createHttpClient();
        Response response = client.newCall(builderRequest.build()).execute();
        String s = response.headers().value(1);
        long timeServer = DateHelper.parse(context, s).getTimeInSeconds();
        response.close();
        long timeDevice = DateHelper.initNow(context).getTimeInSeconds();
        int timeDiff = (int) (timeDevice - timeServer);
        SharedPreferences pref = context.getSharedPreferences(Const.PROM, Context.MODE_PRIVATE);
        if (timeDiff != pref.getInt(Const.TIMEDIFF, 0)) {
            SharedPreferences.Editor editor = pref.edit();
            editor.putInt(Const.TIMEDIFF, timeDiff);
            editor.apply();
            SlashModel.getInstance().postProgress(new Data.Builder()
                    .putBoolean(Const.TIME, true)
                    .build());
        }
    }
}
