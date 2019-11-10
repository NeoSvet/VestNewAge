package ru.neosvet.vestnewage.service;

import android.arch.lifecycle.LifecycleService;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

import androidx.work.Configuration;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import ru.neosvet.utils.ProgressModel;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.helpers.NotificationHelper;
import ru.neosvet.vestnewage.workers.CheckWorker;
import ru.neosvet.vestnewage.workers.LoaderWorker;

/**
 * Created by NeoSvet on 09.11.2019.
 */

public class CheckService extends LifecycleService {
    public static final String TAG = "CheckService";
    private static CheckService srv;

    public static CheckService getService() {
        return srv;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Context context = getApplicationContext();
        NotificationHelper notifHelper = new NotificationHelper(context);
        NotificationCompat.Builder notifBuilder = notifHelper.getNotification(
                context.getResources().getString(R.string.site_name),
                context.getResources().getString(R.string.check_new),
                NotificationHelper.CHANNEL_MUTE);
        startForeground(NotificationHelper.NOTIF_SUMMARY, notifBuilder.build());
        srv = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();
        OneTimeWorkRequest task = new OneTimeWorkRequest
                .Builder(CheckWorker.class)
                .setConstraints(constraints)
                .build();
        Configuration configuration = new Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.INFO)
                .build();
        WorkManager.initialize(getApplicationContext(), configuration);
        WorkManager work = WorkManager.getInstance();
        WorkContinuation job = work.beginUniqueWork(TAG,
                ExistingWorkPolicy.REPLACE, task);
        Data data = new Data.Builder()
                .putString(ProgressModel.NAME, this.getClass().getSimpleName())
                .build();
        task = new OneTimeWorkRequest
                .Builder(LoaderWorker.class)
                .setConstraints(constraints)
                .setInputData(data)
                .build();
        job = job.then(task);
        job.enqueue();
        return super.onStartCommand(intent, flags, startId);
    }

    public void stop() {
        stopForeground(true);
    }
}