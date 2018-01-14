package ru.neosvet.vestnewage;

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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.neosvet.utils.Lib;

public class SummaryReceiver extends WakefulBroadcastReceiver {
    private static final int notif_id = 111;

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
        Intent service = new Intent(context, Service.class);
        startWakefulService(context, service);
    }

    public static class Service extends IntentService {
        public Service() {
            super("Summary");
        }

        @Override
        protected void onHandleIntent(final Intent intent) {
            final Context context = getApplicationContext();
            SharedPreferences pref = context.getSharedPreferences(SettingsFragment.SUMMARY, MODE_PRIVATE);
            final int p = pref.getInt(SettingsFragment.TIME, -1);
            if (p == -1)
                return;
            final boolean boolSound = pref.getBoolean(SettingsFragment.SOUND, false);
            final boolean boolVibr = pref.getBoolean(SettingsFragment.VIBR, true);
            try {
                OkHttpClient.Builder builderClient = new OkHttpClient.Builder();
                builderClient.connectTimeout(Lib.TIMEOUT, TimeUnit.SECONDS);
                builderClient.readTimeout(Lib.TIMEOUT, TimeUnit.SECONDS);
                builderClient.writeTimeout(Lib.TIMEOUT, TimeUnit.SECONDS);

                Request.Builder builderRequest = new Request.Builder();
                builderRequest.url(Lib.SITE + "rss/");
                builderRequest.header("User-Agent", context.getPackageName());
                OkHttpClient client = builderClient.build();
                Response response = client.newCall(builderRequest.build()).execute();

                String s, title, link, des;
                BufferedReader br = new BufferedReader(response.body().charStream(), 1000);
                s = br.readLine();
                while (!s.contains("item"))
                    s = br.readLine();
                title = br.readLine();
                link = withOutTag(br.readLine());
                des = br.readLine();
                s = br.readLine();
                setReceiver(context, p); //настраиваем следующую проверку
                File f = new File(context.getFilesDir() + SummaryFragment.RSS);
                long t = 0;
                if (f.exists())
                    t = f.lastModified();
                if (t > Date.parse(withOutTag(s))) { //список в загрузке не нуждается
                    br.close();
                    return;
                }
                final Uri notif_uri = Uri.parse(link);
                final String notif_text = context.getResources().getString(R.string.appeared_new) + withOutTag(title);
                BufferedWriter bw = new BufferedWriter(new FileWriter(f));
                do {
                    bw.write(withOutTag(title));
                    bw.write(Lib.N);
                    if (link.contains(Lib.SITE))
                        bw.write(Lib.LINK + link.substring(Lib.SITE.length()));
                    else
                        bw.write(link);
                    bw.write(Lib.N);
                    bw.write(withOutTag(des));
                    bw.write(Lib.N);
                    bw.write(Date.parse(withOutTag(s)) + Lib.N); //time
                    bw.flush();
                    s = br.readLine(); //</item><item> or </channel>
                    if (s.contains("</channel>")) break;
                    title = br.readLine();
                    link = withOutTag(br.readLine());
                    des = br.readLine();
                    s = br.readLine(); //time
                } while (s != null);
                bw.close();
                br.close();

                Intent app = new Intent(context, SlashActivity.class);
                app.setData(notif_uri);
                PendingIntent piEmpty = PendingIntent.getActivity(context, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
                PendingIntent piSummary = PendingIntent.getActivity(context, 0, app, PendingIntent.FLAG_UPDATE_CURRENT);
                NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
                        .setSmallIcon(R.drawable.star)
                        .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
                        .setContentTitle(context.getResources().getString(R.string.site_name))
                        .setContentText(notif_text)
                        .setTicker(notif_text)
                        .setWhen(System.currentTimeMillis())
                        .setFullScreenIntent(piEmpty, true)
                        .setContentIntent(piSummary)
                        .setLights(Color.GREEN, 1000, 1000)
                        .setAutoCancel(true);
                if (boolSound)
                    mBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                if (boolVibr)
                    mBuilder.setVibrate(new long[]{500, 1500});
                nm.notify(notif_id, mBuilder.build());
            } catch (Exception e) {
                e.printStackTrace();
            }
            SummaryReceiver.completeWakefulIntent(intent);
        }

        private String withOutTag(String s) {
            int i = s.indexOf(">") + 1;
            s = s.substring(i, s.indexOf("<", i));
            return s;
        }
    }
}
