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
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;

import ru.neosvet.vestnewage.R;

/**
 * Created by NeoSvet on 13.02.2018.
 * Helper class to manage notification channels, and create notifications.
 */
public class NotificationHelper extends ContextWrapper {
    private NotificationManager manager;
    public static final int NOTIF_SUMMARY = 111, ID_SUMMARY = 2, ID_SUMMARY_POSTPONE = 3;
    public static final int NOTIF_PROM = 222, ID_ACCEPT = 1;
    public static final String CHANNEL_SUMMARY = "summary", CHANNEL_MUTE = "mute",
            CHANNEL_PROM = "prom", CHANNEL_TIPS = "tips", MODE = "mode",
            GROUP_SUMMARY = "group_summary", GROUP_TIPS = "group_tips";
    private List<String> notifList;

    public static class Result extends Activity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            int mode = getIntent().getIntExtra(MODE, -1);
            NotificationHelper notifHelper = new NotificationHelper(this);
            if (mode == ID_ACCEPT) {
                notifHelper.cancel(NOTIF_PROM);
            } else if (mode == ID_SUMMARY_POSTPONE) {
                notifHelper.cancel(getIntent().getIntExtra(DataBase.ID, 0));
                SummaryHelper.postpone(this,
                        getIntent().getStringExtra(DataBase.DESCTRIPTION),
                        getIntent().getStringExtra(DataBase.LINK));
            }
            finish();
        }
    }

    public PendingIntent getCancelPromNotif() {
        Intent intent = new Intent(this, NotificationHelper.Result.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(MODE, ID_ACCEPT);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        return pendingIntent;
    }

    public PendingIntent getPostponeSummaryNotif(int id, String des, String link) {
        Intent intent = new Intent(this, NotificationHelper.Result.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(MODE, ID_SUMMARY_POSTPONE);
        intent.putExtra(DataBase.DESCTRIPTION, des);
        intent.putExtra(DataBase.LINK, link);
        intent.putExtra(DataBase.ID, id);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, id, intent, PendingIntent.FLAG_CANCEL_CURRENT);
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
            if (getManager().getNotificationChannels().size() == 0) // no channels
                createChannels();
        }
    }

    public void cancel(int id) {
        getManager().cancel(id);
    }

    @RequiresApi(26)
    private void createChannels() {
        NotificationChannel chSummary = new NotificationChannel(CHANNEL_SUMMARY,
                getString(R.string.notif_new), NotificationManager.IMPORTANCE_HIGH);
        chSummary.enableLights(true);
        chSummary.setLightColor(Color.GREEN);
        chSummary.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        getManager().createNotificationChannel(chSummary);

        NotificationChannel chProm = new NotificationChannel(CHANNEL_PROM,
                getString(R.string.notif_prom), NotificationManager.IMPORTANCE_HIGH);
        chSummary.enableLights(true);
        chProm.setLightColor(Color.RED);
        chProm.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        getManager().createNotificationChannel(chProm);

        NotificationChannel chTips = new NotificationChannel(CHANNEL_TIPS,
                getString(R.string.tips), NotificationManager.IMPORTANCE_HIGH);
        chTips.setSound(null, null);
        chTips.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        getManager().createNotificationChannel(chTips);

        NotificationChannel chMute = new NotificationChannel(CHANNEL_MUTE,
                getString(R.string.mute_notif), NotificationManager.IMPORTANCE_LOW);
        chMute.setSound(null, null);
        chMute.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        getManager().createNotificationChannel(chMute);
    }

    /**
     * Get a notification of type 1
     * <p>
     * Provide the builder rather than the notification it's self as useful for making notification
     * changes.
     *
     * @param title the title of the notification
     * @param msg   the msg text for the notification
     * @return the builder as it keeps a reference to the notification (since API 24)
     */
    public NotificationCompat.Builder getNotification(String title, String msg, String channel) {
        NotificationCompat.Builder notifBuilder;
        notifBuilder = new NotificationCompat.Builder(getApplicationContext(), channel);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            if (notifList == null)
                notifList = new ArrayList<>();
            notifList.add(title + " " + msg);
        }
        notifBuilder.setContentTitle(title)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setContentText(msg)
                .setSmallIcon(R.drawable.star)
                .setAutoCancel(true);
        if (msg.length() > 44)
            notifBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(msg));
        return notifBuilder;
    }

    public NotificationCompat.Builder getSummaryNotif(String title, String channel) {
        NotificationCompat.Builder notifBuilder;
        notifBuilder = new NotificationCompat.Builder(this, channel);
        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle()
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
            notifBuilder.setFullScreenIntent(piEmpty, false)
                    .setWhen(System.currentTimeMillis()).setShowWhen(true);
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
    public void notify(int id, NotificationCompat.Builder notification) {
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
