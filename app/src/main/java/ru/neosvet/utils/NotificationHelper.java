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

import java.util.ArrayList;
import java.util.List;

import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.service.InitJobService;
import ru.neosvet.vestnewage.service.SummaryService;

/**
 * Created by NeoSvet on 13.02.2018.
 * Helper class to manage notification channels, and create notifications.
 */
public class NotificationHelper extends ContextWrapper {
    private NotificationManager manager;
    public static final String CHANNEL_SUMMARY = "summary", CHANNEL_MUTE = "mute",
            CHANNEL_PROM = "prom", CHANNEL_TIPS = "tips", MODE = "mode",
            GROUP_SUMMARY = "group_summary", GROUP_TIPS = "group_tips";
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
            if (getManager().getNotificationChannels().size() == 0) // no channels
                createChannels();
        }
    }

    @RequiresApi(26)
    private void createChannels() {
        NotificationChannel chSummary = new NotificationChannel(CHANNEL_SUMMARY,
                getString(R.string.updates_site), NotificationManager.IMPORTANCE_HIGH);
        chSummary.enableLights(true);
        chSummary.setLightColor(Color.GREEN);
        chSummary.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        getManager().createNotificationChannel(chSummary);

        NotificationChannel chProm = new NotificationChannel(CHANNEL_PROM,
                getString(R.string.reminder_prom), NotificationManager.IMPORTANCE_HIGH);
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
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
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
