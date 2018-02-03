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
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.WakefulBroadcastReceiver;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.concurrent.TimeoutException;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.Noread;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.SlashActivity;
import ru.neosvet.vestnewage.fragment.SettingsFragment;
import ru.neosvet.vestnewage.fragment.SummaryFragment;
import ru.neosvet.vestnewage.task.LoaderTask;

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
        private Context context;

        public Service() {
            super("Summary");
        }

        @Override
        protected void onHandleIntent(final Intent intent) {
            context = getApplicationContext();
            SharedPreferences pref = context.getSharedPreferences(SettingsFragment.SUMMARY, MODE_PRIVATE);
            final int p = pref.getInt(SettingsFragment.TIME, -1);
            if (p == -1)
                return;
            try {
                String[] result = checkSummary();
                setReceiver(context, p); //настраиваем следующую проверку

                if (result == null) return;

                final String notif_text = result[0];
                final Uri notif_uri = Uri.parse(result[1]);
                final boolean boolSound = pref.getBoolean(SettingsFragment.SOUND, false);
                final boolean boolVibr = pref.getBoolean(SettingsFragment.VIBR, true);
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
                if (Build.VERSION.SDK_INT > 23) { // Android 7+
                    if (e instanceof UnknownHostException || e instanceof TimeoutException)
                        setReceiver(context, p);
                }
            }
            SummaryReceiver.completeWakefulIntent(intent);
        }

        private String[] checkSummary() throws Exception {
            Lib lib = new Lib(context);
            InputStream in = new BufferedInputStream(lib.getStream(Const.SITE
                    + "rss/?" + System.currentTimeMillis()));
            BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
            String s, title, link, des;
            s = br.readLine();
            while (!s.contains("item"))
                s = br.readLine();
            title = withOutTag(br.readLine());
            link = parseLink(br.readLine());
            des = withOutTag(br.readLine());
            s = withOutTag(br.readLine()); //time

            File file = new File(context.getFilesDir() + SummaryFragment.RSS);
            long t = 0;
            if (file.exists())
                t = file.lastModified();
            if (t > Date.parse(s)) { //список в загрузке не нуждается
                br.close();
                return null;
            }
            int count_new = 1;
            String[] result = new String[]{context.getResources().getString(R.string.appeared_new) + title, Const.SITE + link};
            BufferedWriter bw = new BufferedWriter(new FileWriter(file));
            Noread noread = new Noread(context);
            Date d;
            do {
                d = new Date(Date.parse(s));
                if (!noread.addLink(link, d)) { //!existsPage
                    count_new++;
                    downloadPage(link);
                }
                bw.write(title);
                bw.write(Const.N);
                bw.write(link);
                bw.write(Const.N);
                bw.write(des);
                bw.write(Const.N);
                bw.write(d.getTime() + Const.N); //time
                bw.flush();
                s = br.readLine(); //</item><item> or </channel>
                if (s.contains("</channel>")) break;
                title = withOutTag(br.readLine());
                link = parseLink(br.readLine());
                des = withOutTag(br.readLine());
                s = withOutTag(br.readLine()); //time
            } while (s != null);
            bw.close();
            br.close();
            noread.close();
            if (count_new > 1) {
                result = new String[]{context.getResources().getString(R.string.appeared_new_some), Const.SITE + SummaryFragment.RSS};
            }
            return result;
        }

        private void downloadPage(String link) {
            LoaderTask loader = new LoaderTask(context);
            loader.execute(link, DataBase.LINK);
        }

        private String withOutTag(String s) {
            int i = s.indexOf(">") + 1;
            s = s.substring(i, s.indexOf("<", i));
            return s;
        }

        private String parseLink(String s) {
            s = withOutTag(s);
            if (s.contains(Const.SITE2))
                s = s.substring(Const.SITE2.length());
            else if (s.contains(Const.SITE))
                s = s.substring(Const.SITE.length());
            return s;
        }
    }
}
