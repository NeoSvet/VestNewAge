package ru.neosvet.vestnewage.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.NotificationHelper;
import ru.neosvet.utils.Unread;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.SlashActivity;
import ru.neosvet.vestnewage.fragment.SettingsFragment;
import ru.neosvet.vestnewage.fragment.SummaryFragment;
import ru.neosvet.vestnewage.task.LoaderTask;

/**
 * Created by NeoSvet on 10.02.2018.
 */

public class SummaryService extends JobIntentService {
    private Context context;
    public static final int notif_id = 111;

    public static void cancelNotif(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(notif_id);
    }

    static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, InitJobService.class, notif_id, work);
    }

//    public SummaryService() {
//        super("Summary");
//    }

    @Override
    protected void onHandleWork(@NonNull final Intent intent) {
        context = getApplicationContext();
        SharedPreferences pref = context.getSharedPreferences(SettingsFragment.SUMMARY, MODE_PRIVATE);
        try {
            String[] result;
            if (intent.hasExtra(DataBase.LINK)) {
                result = new String[]{intent.getStringExtra(DataBase.DESCTRIPTION),
                        intent.getStringExtra(DataBase.LINK)};
            } else {
//                if (pref.getInt(SettingsFragment.TIME, -1) == -1)
//                    return;
                result = checkSummary();
            }
            if (result == null) { // no updates
                Intent finish = new Intent(InitJobService.ACTION_FINISHED);
                context.sendBroadcast(finish);
                return;
            }

            final String notif_text = result[0];
            final Uri notif_uri = Uri.parse(result[1]);
            final boolean sound = pref.getBoolean(SettingsFragment.SOUND, false);
            final boolean vibration = pref.getBoolean(SettingsFragment.VIBR, true);
            Intent app = new Intent(context, SlashActivity.class);
            app.setData(notif_uri);
            PendingIntent piEmpty = PendingIntent.getActivity(context, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent piSummary = PendingIntent.getActivity(context, 0, app, PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent piPostpone = NotificationHelper.getPostponeSummaryNotif(context, result[0], result[1]);
            NotificationHelper notifHelper = new NotificationHelper(context);
            Notification.Builder notifBuilder = notifHelper.getNotification(
                    context.getResources().getString(R.string.site_name),
                    notif_text, NotificationHelper.CHANNEL_NOTIFICATIONS);
            notifBuilder.setFullScreenIntent(piEmpty, true)
                    .setContentIntent(piSummary)
                    .addAction(0, context.getResources().getString(R.string.postpone), piPostpone)
                    .setLights(Color.GREEN, 1000, 1000);
            if (result.length == 3)
                notifBuilder.setNumber(Integer.parseInt(result[2]));
            if (sound)
                notifBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            if (vibration)
                notifBuilder.setVibrate(new long[]{500, 1500});
            notifHelper.notify(notif_id, notifBuilder);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Intent finish = new Intent(InitJobService.ACTION_FINISHED);
        context.sendBroadcast(finish);
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
        int count_new = 0;
        String[] result = new String[]{context.getResources().getString(R.string.appeared_new) + title, Const.SITE + link};
        BufferedWriter bw = new BufferedWriter(new FileWriter(file));
        Unread unread = new Unread(context);
        Date d;
        LoaderTask loader = new LoaderTask(context);
        loader.initClient();
        do {
            d = new Date(Date.parse(s));
            if (unread.addLink(link, d)) {
                count_new++;
                loader.downloadPage(link, true);
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
        unread.setBadge();
        unread.close();
        if (count_new > 1) {
            result = new String[]{context.getResources().getString(R.string.appeared_new_some),
                    Const.SITE + SummaryFragment.RSS, Integer.toString(count_new)};
        }
        return result;
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