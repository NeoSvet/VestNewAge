package ru.neosvet.utils;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.service.InitJobService;
import ru.neosvet.vestnewage.service.SummaryService;

/**
 * Created by NeoSvet on 13.02.2018.
 * Helper class to manage notification channels, and create notifications.
 */
//@RequiresApi(26)
public class NotificationHelper extends ContextWrapper {
    private NotificationManager manager;
    public static final String CHANNEL_NOTIFICATIONS = "notif", CHANNEL_TIPS = "tips",
            MODE = "mode", GROUP_TIPS = "group_tips";
    private List<String> notifList;

    public static class Result extends Activity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            int mode = getIntent().getIntExtra(MODE, -1);
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (mode == Prom.notif_id) {
                manager.cancel(Prom.notif_id);
            } else if (mode == InitJobService.ID_SUMMARY_POSTPONE) {
                manager.cancel(SummaryService.notif_id);
                String des = getIntent().getStringExtra(DataBase.DESCTRIPTION);
                String link = getIntent().getStringExtra(DataBase.LINK);
                InitJobService.setSummaryPostpone(this, des, link);
            }
            finish();
        }
    }

    public static PendingIntent getCancelPromNotif(Context context) {
        Intent intent = new Intent(context, NotificationHelper.Result.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(MODE, Prom.notif_id);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        return pendingIntent;
    }

    public static PendingIntent getPostponeSummaryNotif(Context context, String des, String link) {
        Intent intent = new Intent(context, NotificationHelper.Result.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(MODE, InitJobService.ID_SUMMARY_POSTPONE);
        intent.putExtra(DataBase.DESCTRIPTION, des);
        intent.putExtra(DataBase.LINK, link);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        return pendingIntent;
    }

    /**
     * Registers notification channels, which can be used later by individual notifications.
     *
     * @param context The application context
     */
    public NotificationHelper(Context context) {
        super(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan1 = new NotificationChannel(CHANNEL_NOTIFICATIONS,
                    getString(R.string.notifications), NotificationManager.IMPORTANCE_HIGH);
            chan1.setLightColor(Color.GREEN);
            chan1.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            getManager().createNotificationChannel(chan1);

            NotificationChannel chan2 = new NotificationChannel(CHANNEL_TIPS,
                    getString(R.string.tips), NotificationManager.IMPORTANCE_HIGH);
            chan2.setLightColor(Color.BLUE);
            chan2.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            getManager().createNotificationChannel(chan2);
        }
    }

    /**
     * Get a notification of type 1
     * <p>
     * Provide the builder rather than the notification it's self as useful for making notification
     * changes.
     *
     * @param title the title of the notification
     * @param msg  the msg text for the notification
     * @return the builder as it keeps a reference to the notification (since API 24)
     */
    public Notification.Builder getNotification(String title, String msg, String channel) {
        Notification.Builder notifBuilder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            notifBuilder = new Notification.Builder(getApplicationContext(), channel);
        else {
            notifBuilder = new Notification.Builder(getApplicationContext());
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                if (notifList == null)
                    notifList = new ArrayList<>();
                notifList.add(title + " " + msg);
            }
        }
        notifBuilder.setContentTitle(title)
                .setContentText(msg)
                .setSmallIcon(R.drawable.star)
                .setAutoCancel(true);
        if (msg.length() > 44)
            notifBuilder.setStyle(new Notification.BigTextStyle().bigText(msg));
        return notifBuilder;
    }

    public Notification.Builder getSummaryNotif(String title, String channel) {
        Notification.Builder notifBuilder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            notifBuilder = new Notification.Builder(this, channel);
        else
            notifBuilder = new Notification.Builder(this);
        Notification.InboxStyle style = new Notification.InboxStyle()
                .setSummaryText(title);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            style.setBigContentTitle(getResources().getString(R.string.app_name));
            if (notifList != null) {
                for (int i = 0; i < notifList.size(); i++) {
                    style.addLine(notifList.get(i));
                }
                notifList.clear();
            }
            PendingIntent piEmpty = PendingIntent.getActivity(this, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
            notifBuilder.setFullScreenIntent(piEmpty, false);
        }
        notifBuilder.setContentTitle(getResources().getString(R.string.app_name))
                .setSmallIcon(R.drawable.star)
                .setContentText(title)
                .setStyle(style)
                .setGroupSummary(true);
        return notifBuilder;
    }

    /**
     * Send a notification.
     *
     * @param id           The ID of the notification
     * @param notification The notification object
     */
    public void notify(int id, Notification.Builder notification) {
        getManager().notify(id, notification.build());
    }

    /**
     * Get the notification manager.
     * <p>
     * Utility method as this helper works with it a lot.
     *
     * @return The system service NotificationManager
     */
    private NotificationManager getManager() {
        if (manager == null) {
            manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }
        return manager;
    }
}
