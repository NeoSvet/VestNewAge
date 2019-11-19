package ru.neosvet.vestnewage.helpers;

import android.app.NotificationManager;
import android.app.PendingIntent;
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
import ru.neosvet.vestnewage.activity.MainActivity;

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
        PendingIntent piEmpty = PendingIntent.getActivity(context, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
        Intent main = new Intent(context, MainActivity.class);
        PendingIntent pMain = PendingIntent.getActivity(context, 0, main, PendingIntent.FLAG_UPDATE_CURRENT);
        notif = notifHelper.getNotification(
                context.getResources().getString(R.string.load),
                context.getResources().getString(R.string.start),
                NotificationHelper.CHANNEL_MUTE)
                .setContentIntent(pMain)
                .setAutoCancel(false)
                .setProgress(0, 0, true)
                .setFullScreenIntent(piEmpty, true);
        startForeground(notif_id, notif.build());
    }

    public static void postCommand(Context context, String name, boolean stop) {
        Intent intent = new Intent(context, LoaderHelper.class);
        intent.putExtra(Const.TITLE, name);
        if (stop)
            intent.putExtra(Const.CHECK, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.startForegroundService(intent);
        else
            context.startService(intent);
    }

    public static void checkObserve(ProgressModel model) {
        if (!model.getProgress().hasObservers())
            postCommand(model.getApplication().getBaseContext(), model.getClass().getSimpleName(), true);
    }

    @Override
    public int onStartCommand(Intent intent, final int flags, int startId) {
        name = intent.getStringExtra(Const.TITLE);
        if (intent.getBooleanExtra(Const.CHECK, false)) {
            Lib.LOG("LoaderHelper finish");
            start = false;
            ProgressModel model = ProgressModel.getModelByName(name);
            if (model != null)
                model.finish();
            return super.onStartCommand(intent, flags, startId);
        }
        if (intent.getBooleanExtra(Const.END, false)) {
            Lib.LOG("LoaderHelper cancel");
            start = false;
            ProgressModel model = ProgressModel.getModelByName(name);
            if (model != null)
                model.cancel = true;
            return super.onStartCommand(intent, flags, startId);
        }
        Lib.LOG("LoaderHelper start");
        Context context = getApplicationContext();
        Intent iStop = new Intent(context, LoaderHelper.class);
        iStop.putExtra(Const.TITLE, name);
        iStop.putExtra(Const.END, true);
        PendingIntent piStop = PendingIntent.getService(context, 0, iStop, PendingIntent.FLAG_CANCEL_CURRENT);
        notif.addAction(0, context.getResources().getString(R.string.stop), piStop);
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
                    stopForeground(true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
        return super.onStartCommand(intent, flags, startId);
    }
}