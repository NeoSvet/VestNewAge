package ru.neosvet.vestnewage.helpers;

import android.app.Service;
import android.arch.lifecycle.LifecycleService;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.NotificationCompat;

import androidx.work.Configuration;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.workers.CheckWorker;
import ru.neosvet.vestnewage.workers.LoaderWorker;

/**
 * Created by NeoSvet on 09.11.2019.
 */

public class CheckHelper extends LifecycleService {
    public static final String TAG = "CheckHelper";

    @Override
    public void onCreate() {
        super.onCreate();
        Context context = getApplicationContext();
        NotificationHelper notifHelper = new NotificationHelper(context);
        NotificationCompat.Builder notifBuilder = notifHelper.getNotification(
                context.getResources().getString(R.string.site_name),
                context.getResources().getString(R.string.check_new),
                NotificationHelper.CHANNEL_MUTE)
                .setProgress(0, 0, true);
        startForeground(NotificationHelper.NOTIF_SUMMARY, notifBuilder.build());
    }

    public static void postCommand(Context context, boolean start) {
        Intent intent = new Intent(context, CheckHelper.class);
        intent.putExtra(Const.START, start);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.startForegroundService(intent);
        else
            context.startService(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!intent.getBooleanExtra(Const.START, false)) {
            stopForeground(true);
            return Service.START_NOT_STICKY;
        }
        try {
            Configuration configuration = new Configuration.Builder()
                    .setMinimumLoggingLevel(android.util.Log.INFO)
                    .build();
            WorkManager.initialize(getApplicationContext(), configuration);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Data data = new Data.Builder()
                .putString(Const.TASK, TAG).build();
        WorkManager work = WorkManager.getInstance();
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