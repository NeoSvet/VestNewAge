package ru.neosvet.vestnewage;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;

import java.util.Date;

import ru.neosvet.utils.Lib;
import ru.neosvet.utils.Prom;

import static android.content.Context.MODE_PRIVATE;


public class PromReceiver extends BroadcastReceiver {
    private static final int notif_id = 222, hour_prom = 11;

//    public static void cancelNotif(Context context) {
//        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
//        nm.cancel(notif_id);
//    }

    public static void setReceiver(Context context, int p) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, PromReceiver.class);
//        Date d = new Date(am.getNextAlarmClock().getTriggerTime());
//        Lib.LOG("next="+d.toString());
        if (p > -1) {
            PendingIntent piProm = PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            am.set(1, getPromDate(p).getTime(), piProm); //System.currentTimeMillis()+3000
        } else {
            PendingIntent piCancel = PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_NO_CREATE);
            am.cancel(piCancel);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Lib.LOG("onReceive");
        SharedPreferences pref = context.getSharedPreferences(SettingsFragment.PROM, MODE_PRIVATE);
        int p = pref.getInt(SettingsFragment.TIME, -1);
        if (p == -1)
            return;
        boolean boolSound = pref.getBoolean(SettingsFragment.SOUND, false);
        boolean boolVibr = pref.getBoolean(SettingsFragment.VIBR, true);
        intent = new Intent(context, SlashActivity.class);
        intent.setData(Uri.parse(Lib.SITE + "Posyl-na-Edinenie.html"));
        PendingIntent piEmpty = PendingIntent.getActivity(context, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent piProm = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Prom prom = new Prom(context);
        String msg = prom.getPromText();
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.star)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
                .setContentTitle(context.getResources().getString(R.string.prom_for_soul_unite))
                .setContentText(msg)
                .setTicker(msg)
                .setWhen(System.currentTimeMillis())
                .setFullScreenIntent(piEmpty, true)
                .setContentIntent(piProm)
                .setLights(Color.GREEN, 1000, 1000)
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setAutoCancel(true);
        if (boolSound)
            mBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
        if (boolVibr)
            mBuilder.setVibrate(new long[]{500, 1500});
        Lib.LOG("notif");
        nm.notify(notif_id, mBuilder.build());
//        try {
//            Thread.sleep(60000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        setReceiver(context, p);
    }

    private static Date getPromDate(int q) {
        Date d = new Date();
        int m = d.getHours() * 60 + d.getMinutes() + q;
        // int p = 11 * 60 + d.getTimezoneOffset() + 180;
        int p = hour_prom * 60 - d.getTimezoneOffset() - 180;
        int n = d.getDate();
        if (m > p) {
            d.setDate(n + 1);
            n = d.getDate();
        }
        if (n <= 4)
            d.setDate(4);
        else if (n <= 17)
            d.setDate(17);
        else if (n <= 26)
            d.setDate(26);
        else if (n <= 30) {
            m = d.getMonth();
            d.setDate(30);
            if (m != d.getMonth())
                d.setDate(4);
        }
        d.setSeconds(0);
        if (d.getTimezoneOffset() == -180) {
            d.setHours(hour_prom);
            d.setMinutes(-q);
        } else {
            d.setHours(0);
            d.setMinutes(p - q);
        }
        Lib.LOG("date prom=" + d.toString() + " (" + q + ")");
        return d;
    }

//    private String getPromTime(Context context) {
//        Date d = getMoscowDate();
//        long now = d.getTime();
//        d.setHours(hour_prom);
//        d.setMinutes(0);
//        d.setSeconds(0);
//        Lib lib = new Lib(context);
//        String t = lib.getDiffDate(d.getTime(), now);
//        if (t.contains("-"))
//            return context.getResources().getString(R.string.prom);
//        t = context.getResources().getString(R.string.to_prom)
//                + " " + t.substring(0, t.length() - 6);
//        for (int i = 0; i < context.getResources().getStringArray(R.array.time).length; i++) {
//            if (t.contains(context.getResources().getStringArray(R.array.time)[i])) {
//                if (i == 0 && !t.contains("1"))
//                    t = context.getResources().getString(R.string.prom);
//                else if (i < 3)
//                    t = t.replace(context.getResources().getStringArray(R.array.time)[i]
//                            , context.getResources().getString(R.string.seconde));
//                if (i == 3)
//                    t = t.replace(context.getResources().getStringArray(R.array.time)[i]
//                            , context.getResources().getString(R.string.minute));
//                break;
//            }
//        }
//        return t;
//    }
}
