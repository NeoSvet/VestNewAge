package ru.neosvet.vestnewage.receiver;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.threeten.bp.Clock;
import org.threeten.bp.temporal.ChronoField;
import org.threeten.bp.temporal.ChronoUnit;
import org.threeten.bp.temporal.Temporal;

import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.PromHelper;


public class PromReceiver extends BroadcastReceiver {

    public static void setReceiver(Context context, int p) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, PromReceiver.class);
        PendingIntent piProm = PendingIntent.getBroadcast(context, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(piProm);
        if (p > -1) {
            PromHelper prom = new PromHelper(context, null);
            Temporal d = prom.getPromDate(false);
            d.minus(p, ChronoUnit.MINUTES);
            Temporal now = Clock.systemUTC().instant();
            if (d.get(ChronoField.INSTANT_SECONDS) < now.get(ChronoField.INSTANT_SECONDS)) {
                d = prom.getPromDate(true);
                d.minus(p, ChronoUnit.MINUTES);
            }
            if (p == 0)
                d.minus(30, ChronoUnit.SECONDS);
            long time = d.get(ChronoField.INSTANT_SECONDS) * 1000;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.AlarmClockInfo(time, piProm);
                am.setAlarmClock(alarmClockInfo, piProm);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                am.setExact(AlarmManager.RTC_WAKEUP, time, piProm);
            else
                am.set(AlarmManager.RTC_WAKEUP, time, piProm);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        PromHelper prom = new PromHelper(context, null);
        prom.showNotif();
    }
}