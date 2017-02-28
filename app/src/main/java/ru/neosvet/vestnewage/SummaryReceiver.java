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
import android.support.v4.app.NotificationCompat;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;

import ru.neosvet.utils.Lib;

import static android.content.Context.MODE_PRIVATE;

public class SummaryReceiver extends BroadcastReceiver {
    private static final int notif_id = 111;

    public static void cancelNotif(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(notif_id);
    }

    public static void setReceiver(Context context, int p) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, SummaryReceiver.class);
        if (p > -1) {
            p = (p + 1) * 600000;
            PendingIntent piCheck = PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            am.set(1, p + System.currentTimeMillis(), piCheck);
        } else {
            PendingIntent piCancel = PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_NO_CREATE);
            am.cancel(piCancel);
        }
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        SharedPreferences pref = context.getSharedPreferences(SettingsFragment.SUMMARY, MODE_PRIVATE);
        final int p = pref.getInt(SettingsFragment.TIME, -1);
        if (p == -1)
            return;
        final boolean boolSound = pref.getBoolean(SettingsFragment.SOUND, false);
        final boolean boolVibr = pref.getBoolean(SettingsFragment.VIBR, true);
        new Thread(new Runnable() {
            public void run() {
                try {
                    DefaultHttpClient client = new DefaultHttpClient();
                    HttpGet req = new HttpGet(Lib.SITE + "rss/");
                    HttpResponse res = client.execute(req);
                    HttpEntity entity = res.getEntity();
                    BufferedReader br;
                    String s;
                    InputStream in = new BufferedInputStream(entity.getContent());
                    br = new BufferedReader(new InputStreamReader(in), 1000);
                    s = br.readLine();
                    while (!s.contains("pubDate"))
                        s = br.readLine();
                    br.close();
                    in.close();
                    File f = new File(context.getFilesDir() + SummaryFragment.RSS);
                    if (f.exists()) {
                        int i = s.indexOf(">") + 1;
                        s = s.substring(i, s.indexOf("<", i));
                        long t = Date.parse(s);
                        if (t < f.lastModified()) {
                            setReceiver(context, p);
                            return;
                        }
                    }
                    Intent intent = new Intent(context, MainActivity.class);
                    intent.putExtra(SummaryFragment.RSS, true);
                    PendingIntent piEmpty = PendingIntent.getActivity(context, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
                    PendingIntent piSummary = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                    NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
                            .setSmallIcon(R.drawable.star)
                            .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
                            .setContentTitle(context.getResources().getString(R.string.site_name))
                            .setContentText(context.getResources().getString(R.string.appeared_new))
                            .setTicker(context.getResources().getString(R.string.appeared_new))
                            .setWhen(System.currentTimeMillis())
                            .setFullScreenIntent(piEmpty, true)
                            .setContentIntent(piSummary)
                            .setLights(Color.GREEN, 1000, 1000)
                            .setPriority(Notification.PRIORITY_DEFAULT)
                            .setAutoCancel(true);
                    if (boolSound)
                        mBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                    if (boolVibr)
                        mBuilder.setVibrate(new long[]{500, 1500});
                    nm.notify(notif_id, mBuilder.build());
                    setReceiver(context, p);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}
