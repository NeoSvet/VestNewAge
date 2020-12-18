package ru.neosvet.vestnewage.helpers;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LifecycleService;
import androidx.work.Configuration;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.workers.SlashWorker;

/**
 * Created by NeoSvet on 18.12.2020.
 */

public class SlashHelper extends LifecycleService {
    public static final String TAG = "SlashHelper";

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
        startForeground(NotificationHelper.NOTIF_SLASH, notifBuilder.build());
    }

    public static void postCommand(Context context, boolean start) {
        Intent intent = new Intent(context, SlashHelper.class);
        intent.putExtra(Const.START, start);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.startForegroundService(intent);
        else
            context.startService(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !intent.getBooleanExtra(Const.START, false)) {
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
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();
        OneTimeWorkRequest task = new OneTimeWorkRequest
                .Builder(SlashWorker.class)
                .setConstraints(constraints)
                .build();
        WorkContinuation job = WorkManager.getInstance().beginUniqueWork(TAG,
                ExistingWorkPolicy.REPLACE, task);
        job.enqueue();
        return super.onStartCommand(intent, flags, startId);
    }
}