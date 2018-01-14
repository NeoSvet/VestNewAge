package ru.neosvet.vestnewage.receiver;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.WakefulBroadcastReceiver;

import java.util.Date;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.Prom;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.SlashActivity;
import ru.neosvet.vestnewage.fragment.SettingsFragment;


public class PromReceiver extends WakefulBroadcastReceiver {
    public static final int notif_id = 222, hour_prom = 11;

    public static void setReceiver(Context context, int p, boolean boolNext) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, PromReceiver.class);
        PendingIntent piProm = PendingIntent.getBroadcast(context, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(piProm);
        if (p > -1) {
            am.set(AlarmManager.RTC_WAKEUP, getPromDate(p, boolNext).getTime(), piProm); //System.currentTimeMillis()+3000
        }
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        Intent service = new Intent(context, Service.class);
        startWakefulService(context, service);
    }

    private static Date getPromDate(int q, boolean boolNext) {
        Date d = new Date();
        int m = d.getHours() * 60 + d.getMinutes() + q;
        // int p = 11 * 60 - d.getTimezoneOffset() - 180;
        int p = hour_prom * 60 - d.getTimezoneOffset() - 180;
        if (boolNext)
            d.setDate(d.getDate() + 1);
        int n = d.getDate();
        if (m > p)
            d.setDate(++n);

//        while (d.getDay() != 3) tut uncomment // wednesday
//            d.setDate(++n); tut uncomment

//        if (d.getTimezoneOffset() == -180) {
//            d.setHours(hour_prom);
//            d.setMinutes(-q);
//        } else {
        d.setHours(0);
        d.setMinutes(p - q);
//        }
        if (q > 0)
            d.setSeconds(-3);
        else
            d.setSeconds(-30);
        return d;
    }

    public static class Service extends IntentService {
        public Service() {
            super("Prom");
        }

        @Override
        protected void onHandleIntent(Intent intent) {
            Context context = getApplicationContext();
            SharedPreferences pref = context.getSharedPreferences(SettingsFragment.PROM, MODE_PRIVATE);
            final int p = pref.getInt(SettingsFragment.TIME, -1);
            if (p == -1)
                return;
            boolean boolSound = pref.getBoolean(SettingsFragment.SOUND, false);
            boolean boolVibr = pref.getBoolean(SettingsFragment.VIBR, true);
            intent = new Intent(context, SlashActivity.class);
            intent.setData(Uri.parse(Const.SITE + "Posyl-na-Edinenie.html"));
            PendingIntent piEmpty = PendingIntent.getActivity(context, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent piProm = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            Prom prom = new Prom(context);
            String msg = prom.getPromText();
            if (msg.contains("-")) {
                msg = context.getResources().getString(R.string.prom);
            }
            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
                    .setSmallIcon(R.drawable.star)
                    .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
                    .setContentTitle(context.getResources().getString(R.string.prom_for_soul_unite))
                    .setContentText(msg)
                    .setTicker(msg)
                    .setWhen(System.currentTimeMillis() + 3000)
                    .setFullScreenIntent(piEmpty, true)
                    .setContentIntent(piProm)
                    .setLights(Color.GREEN, 1000, 1000)
                    .setAutoCancel(true);
            if (boolSound)
                mBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            if (boolVibr)
                mBuilder.setVibrate(new long[]{500, 1500});
            nm.notify(notif_id, mBuilder.build());
            setReceiver(context, p, true);
            PromReceiver.completeWakefulIntent(intent);
        }
    }
}
