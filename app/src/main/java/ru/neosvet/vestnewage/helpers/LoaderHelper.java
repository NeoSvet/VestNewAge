package ru.neosvet.vestnewage.helpers;

import android.app.NotificationManager;
import android.arch.lifecycle.LifecycleService;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.NotificationCompat;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.ProgressModel;
import ru.neosvet.vestnewage.R;

/**
 * Created by NeoSvet on 19.11.2019.
 */

public class LoaderHelper extends LifecycleService {
    public static final String TAG = "LoaderHelper";
    public static final int notif_id = 777;
    private NotificationCompat.Builder notif;
    private NotificationManager manager;
    private boolean start;
    private String name;

    @Override
    public void onCreate() {
        super.onCreate();
        Context context = getApplicationContext();
        NotificationHelper notifHelper = new NotificationHelper(context);
        notif = notifHelper.getNotification(
                context.getResources().getString(R.string.site_name),
                context.getResources().getString(R.string.load),
                NotificationHelper.CHANNEL_MUTE);
        notif.setProgress(0, 0, true);
        startForeground(notif_id, notif.build());
    }

    public static void start(Context context, String name) {
        Intent intent = new Intent(context, LoaderHelper.class);
        intent.putExtra(Const.TITLE, name);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.startForegroundService(intent);
        else
            context.startService(intent);
    }

    @Override
    public int onStartCommand(Intent intent, final int flags, int startId) {
        Lib.LOG("LoaderHelper start");
        name = intent.getStringExtra(Const.TITLE);
        start = true;
        manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        final Handler handler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                ProgressModel model = ProgressModel.getModelByName(name);
                if (model != null && model.inProgress && !model.cancel) {
                    notif.setContentText(ProgressHelper.getMessage());
                    notif.setProgress(ProgressHelper.getMax(), ProgressHelper.getProg(), false);
                    manager.notify(notif_id, notif.build());
                } else {
                    start = false;
                    stopForeground(true);
                }
                return false;
            }
        });
        new Thread(new Runnable() {
            public void run() {
                try {
                    while (start && ProgressHelper.isBusy()) {
                        Thread.sleep(DateHelper.SEC_IN_MILLS);
                        handler.sendEmptyMessage(0);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
        return super.onStartCommand(intent, flags, startId);
    }
}