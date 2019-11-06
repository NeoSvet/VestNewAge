package ru.neosvet.vestnewage.receiver;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.PromHelper;


public class PromReceiver extends BroadcastReceiver {

    public static void setReceiver(Context context, int p) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, PromReceiver.class);
        PendingIntent piProm = PendingIntent.getBroadcast(context, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(piProm);
        if (p != Const.TURN_OFF) {
            PromHelper prom = new PromHelper(context, null);
            DateHelper d = prom.getPromDate(false);
            if (p == 0)
                d.changeSeconds(-30);
            else
                d.changeMinutes(-p);
            if (d.getTimeInSeconds() < DateHelper.initNow(context).getTimeInSeconds()) {
                d = prom.getPromDate(true);
                if (p == 0)
                    d.changeSeconds(-30);
                else
                    d.changeMinutes(-p);
            }
            long time = d.getTimeInMills();
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