package ru.neosvet.vestnewage.utils;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;

import ru.neosvet.vestnewage.App;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.data.DataBase;
import ru.neosvet.vestnewage.helper.SummaryHelper;

/**
 * Created by NeoSvet on 13.02.2018.
 * Helper class to manage notification channels, and create notifications.
 */
public class NotificationUtils extends ContextWrapper {
    private NotificationManager manager;
    public static final int NOTIF_SUMMARY = 111, ID_SUMMARY_POSTPONE = 3; //ID_SUMMARY = 2,
    public static final int NOTIF_PROM = 222, ID_ACCEPT = 1;
    public static final int NOTIF_CHECK = 333; //, NOTIF_SLASH = 444;
    public static final String CHANNEL_SUMMARY = "summary", CHANNEL_MUTE = "mute",
            CHANNEL_PROM = "prom", CHANNEL_TIPS = "tips", MODE = "mode",
            GROUP_SUMMARY = "group_summary", GROUP_TIPS = "group_tips";
    private List<String> notifList;
    private final int FLAGS = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ?
            PendingIntent.FLAG_CANCEL_CURRENT :
            PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE;

    public static void setAlarm(PendingIntent pi, long time) {
        AlarmManager am = (AlarmManager) App.context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);
        if (time == Const.TURN_OFF)
            return;
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi);
    }

    public static class Result extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int mode = intent.getIntExtra(MODE, -1);
            NotificationUtils notifHelper = new NotificationUtils();
            if (mode == ID_ACCEPT) {
                notifHelper.cancel(NOTIF_PROM);
            } else if (mode == ID_SUMMARY_POSTPONE) {
                notifHelper.cancel(intent.getIntExtra(DataBase.ID, 0));
                SummaryHelper.postpone(
                        intent.getStringExtra(Const.DESCTRIPTION),
                        intent.getStringExtra(Const.LINK));
            }
        }
    }

    public PendingIntent getCancelPromNotif() {
        Intent intent = new Intent(this, NotificationUtils.Result.class);
        intent.putExtra(MODE, ID_ACCEPT);
        return PendingIntent.getBroadcast(this, 0, intent, FLAGS);
    }

    public PendingIntent getPostponeSummaryNotif(int id, String des, String link) {
        Intent intent = new Intent(this, NotificationUtils.Result.class);
        intent.putExtra(MODE, ID_SUMMARY_POSTPONE);
        intent.putExtra(Const.DESCTRIPTION, des);
        intent.putExtra(Const.LINK, link);
        intent.putExtra(DataBase.ID, id);
        return PendingIntent.getBroadcast(this, id, intent, FLAGS);
    }

    /**
     * Registers notification channels, which can be used later by individual notifications.
     */
    public NotificationUtils() {
        super(App.context);
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
     * @param msg   the text text for the notification
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
            style.setBigContentTitle(getString(R.string.app_name));
            if (notifList != null) {
                for (int i = 0; i < notifList.size(); i++) {
                    style.addLine(notifList.get(i));
                }
                notifList.clear();
            }
            @SuppressLint("UnspecifiedImmutableFlag")
            PendingIntent piEmpty = PendingIntent.getActivity(this,
                    0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
            notifBuilder.setFullScreenIntent(piEmpty, false)
                    .setWhen(System.currentTimeMillis()).setShowWhen(true);
        }
        notifBuilder.setContentTitle(getString(R.string.app_name))
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
