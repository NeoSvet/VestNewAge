package ru.neosvet.vestnewage.receiver;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.Date;

import ru.neosvet.utils.Prom;
import ru.neosvet.vestnewage.service.PromService;


public class PromReceiver extends BroadcastReceiver {
    public static final int notif_id = 222;

    public static void setReceiver(Context context, int p) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, PromReceiver.class);
        PendingIntent piProm = PendingIntent.getBroadcast(context, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(piProm);
        if (p > -1) {
            Prom prom = new Prom(context);
            Date d = prom.getPromDate();
            d.setMinutes(d.getMinutes() - p);
            if (p > 0)
                d.setSeconds(d.getSeconds() - 3);
            else
                d.setSeconds(d.getSeconds() - 30);
            if (d.getTime() < System.currentTimeMillis())
                d.setHours(d.getHours() + 24);
            am.set(AlarmManager.RTC_WAKEUP, d.getTime(), piProm);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent service = new Intent(context, PromService.class);
        context.startService(service);
    }
}