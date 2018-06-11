package ru.neosvet.vestnewage.receiver;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.service.SummaryService;

public class SummaryReceiver extends BroadcastReceiver {
    public static final int notif_id = 111;

    public static void cancelNotif(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(notif_id);
    }

    public static void setReceiver(Context context, int p) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, SummaryReceiver.class);
        PendingIntent piCheck = PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(piCheck);
        if (p > -1) {
            p = (p + 1) * 600000;
            am.set(AlarmManager.RTC_WAKEUP, p + System.currentTimeMillis(), piCheck);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent service = new Intent(context, SummaryService.class);
        context.startService(service);
    }
}