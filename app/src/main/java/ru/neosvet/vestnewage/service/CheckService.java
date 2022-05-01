package ru.neosvet.vestnewage.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LifecycleService;
import androidx.work.Configuration;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.App;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.helpers.NotificationHelper;
import ru.neosvet.vestnewage.workers.CheckWorker;
import ru.neosvet.vestnewage.workers.LoaderWorker;

/**
 * Created by NeoSvet on 09.11.2019.
 */

public class CheckService extends LifecycleService {
    public static final String TAG = "CheckService";

    @Override
    public void onCreate() {
        super.onCreate();
    }

    public static void postCommand(boolean start) {
        Intent intent = new Intent(App.context, CheckService.class);
        intent.putExtra(Const.START, start);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            App.context.startForegroundService(intent);
        else
            App.context.startService(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !intent.getBooleanExtra(Const.START, false)) {
            stopForeground(true);
            return Service.START_NOT_STICKY;
        }

        Context context = getApplicationContext();
        NotificationHelper notifHelper = new NotificationHelper();
        NotificationCompat.Builder notifBuilder = notifHelper.getNotification(
                context.getString(R.string.site_name),
                context.getString(R.string.check_new),
                NotificationHelper.CHANNEL_MUTE)
                .setProgress(0, 0, true);
        startForeground(NotificationHelper.NOTIF_CHECK, notifBuilder.build());

        try {
            Configuration configuration = new Configuration.Builder()
                    .setMinimumLoggingLevel(android.util.Log.INFO)
                    .build();
            WorkManager.initialize(getApplicationContext(), configuration);
        } catch (Exception e) {
            //e.printStackTrace();
        }

        Data data = new Data.Builder()
                .putString(Const.TASK, TAG).build();
        WorkManager work = WorkManager.getInstance(context);
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();
        OneTimeWorkRequest task = new OneTimeWorkRequest
                .Builder(CheckWorker.class)
                .setConstraints(constraints)
                .build();
        WorkContinuation job = work.beginUniqueWork(TAG,
                ExistingWorkPolicy.REPLACE, task);
        task = new OneTimeWorkRequest
                .Builder(LoaderWorker.class)
                .setInputData(data)
                .setConstraints(constraints)
                .build();
        job = job.then(task);
        job.enqueue();
        return super.onStartCommand(intent, flags, startId);
    }
}