package ru.neosvet.vestnewage.receiver;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Date;

import ru.neosvet.utils.Prom;


public class PromReceiver extends BroadcastReceiver {

    public static void setReceiver(Context context, int p) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, PromReceiver.class);
        PendingIntent piProm = PendingIntent.getBroadcast(context, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(piProm);
        if (p > -1) {
            Prom prom = new Prom(context, null);
            Date d = prom.getPromDate(false);
            d.setMinutes(d.getMinutes() - p);
            if (d.getTime() < System.currentTimeMillis()) {
                d = prom.getPromDate(true);
                d.setMinutes(d.getMinutes() - p);
            }
            if (p > 0)
                d.setSeconds(d.getSeconds() - 3);
            else
                d.setSeconds(d.getSeconds() - 30);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                am.setExact(AlarmManager.RTC_WAKEUP, d.getTime(), piProm);
            else
                am.set(AlarmManager.RTC_WAKEUP, d.getTime(), piProm);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Prom prom = new Prom(context, null);
        prom.showNotif();
    }
}