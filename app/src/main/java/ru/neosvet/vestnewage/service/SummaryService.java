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
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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

    public static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, SummaryService.class, notif_id, work);
    }

//    public SummaryService() {
//        super("Summary");
//    }

    @Override
    protected void onHandleWork(@NonNull final Intent intent) {
        context = getApplicationContext();
        SharedPreferences pref = context.getSharedPreferences(SettingsFragment.SUMMARY, MODE_PRIVATE);
        try {
            List<String> result;
            if (intent.hasExtra(DataBase.LINK)) {
                result = new ArrayList<>();
                result.add(intent.getStringExtra(DataBase.DESCTRIPTION));
                result.add(intent.getStringExtra(DataBase.LINK));
            } else {
                if (pref.getInt(SettingsFragment.TIME, -1) == -1)
                    return;
                result = checkSummary();
            }
            if (result == null) { // no updates
                Intent finish = new Intent(InitJobService.ACTION_FINISHED);
                context.sendBroadcast(finish);
                return;
            }

            String notif_text;
            Uri notif_uri;
            Intent app = new Intent(context, SlashActivity.class);
            PendingIntent piEmpty = PendingIntent.getActivity(context, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent piSummary, piPostpone;
            NotificationHelper notifHelper = new NotificationHelper(context);
            Notification.Builder notifBuilder = null;

            for (int i = 0; i < result.size(); i += 2) {
                if (notifBuilder != null)
                    notifHelper.notify(notif_id + i, notifBuilder);
                notif_text = result.get(i);
                notif_uri = Uri.parse(Const.SITE + result.get(i + 1));
                app.setData(notif_uri);
                piSummary = PendingIntent.getActivity(context, 0, app, PendingIntent.FLAG_UPDATE_CURRENT);
                piPostpone = NotificationHelper.getPostponeSummaryNotif(context, result.get(i), notif_uri.toString());
                notifBuilder = notifHelper.getNotification(
                        context.getResources().getString(R.string.site_name),
                        notif_text, NotificationHelper.CHANNEL_NOTIFICATIONS);
                notifBuilder.setContentIntent(piSummary)
                        .setGroup(NotificationHelper.GROUP_NOTIFICATIONS)
                        .addAction(0, context.getResources().getString(R.string.postpone), piPostpone);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
                    notifBuilder.setFullScreenIntent(piEmpty, true);
            }
            if (result.size() > 2) {
                notifHelper.notify(notif_id + result.size(), notifBuilder);
                notifBuilder = notifHelper.getSummaryNotif(
                        context.getResources().getString(R.string.appeared_new_some),
                        NotificationHelper.CHANNEL_NOTIFICATIONS);
                app.setData(Uri.parse(Const.SITE + SummaryFragment.RSS));
                piSummary = PendingIntent.getActivity(context, 0, app, PendingIntent.FLAG_UPDATE_CURRENT);
                notifBuilder.setContentIntent(piSummary)
                        .setGroup(NotificationHelper.GROUP_NOTIFICATIONS);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
                    notifBuilder.setFullScreenIntent(piEmpty, true);
            } else {
                notifBuilder.setContentText(context.getResources().getString(R.string.appeared_new) + result.get(0));
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                final boolean sound = pref.getBoolean(SettingsFragment.SOUND, false);
                final boolean vibration = pref.getBoolean(SettingsFragment.VIBR, true);
                notifBuilder.setLights(Color.GREEN, 1000, 1000);
                if (sound)
                    notifBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                if (vibration)
                    notifBuilder.setVibrate(new long[]{500, 1500});
            }
            notifHelper.notify(notif_id, notifBuilder);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Intent finish = new Intent(InitJobService.ACTION_FINISHED);
        context.sendBroadcast(finish);
    }

    private List<String> checkSummary() throws Exception {
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
        List<String> list = new ArrayList<>();
        BufferedWriter bw = new BufferedWriter(new FileWriter(file));
        Unread unread = new Unread(context);
        Date d;
        LoaderTask loader = new LoaderTask(context);
        loader.initClient();
        do {
            d = new Date(Date.parse(s));
            if (unread.addLink(link, d)) {
                loader.downloadPage(link, true);
                list.add(title);
                list.add(link);
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
        return list;
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