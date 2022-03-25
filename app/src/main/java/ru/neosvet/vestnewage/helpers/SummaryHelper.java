package ru.neosvet.vestnewage.helpers;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import ru.neosvet.ui.dialogs.SetNotifDialog;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.App;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.storage.PageStorage;

/**
 * Created by NeoSvet on 11.06.2018.
 */
public class SummaryHelper {
    private static final int TEN_MIN_IN_MILLS = 600000;
    private static final int FLAGS = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ?
            PendingIntent.FLAG_UPDATE_CURRENT :
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;
    private final NotificationHelper notifHelper;
    private final Intent intent;
    private final PendingIntent piEmpty;
    private int notif_id;
    private NotificationCompat.Builder notifBuilder;


    public SummaryHelper() {
        notif_id = NotificationHelper.NOTIF_SUMMARY + 1;
        notifHelper = new NotificationHelper();
        intent = new Intent(App.context, MainActivity.class);
        piEmpty = PendingIntent.getActivity(App.context, 0, new Intent(), FLAGS);
    }

    public void updateBook() throws Exception {
        File file = Lib.getFile(Const.RSS);
        BufferedReader br = new BufferedReader(new FileReader(file));
        String title, link, name;
        PageStorage storage = null;
        ContentValues row;
        Cursor cursor;
        while ((title = br.readLine()) != null) {
            link = br.readLine();
            br.readLine(); //des
            br.readLine(); //time
            name = PageStorage.Companion.getDatePage(link);
            if (storage == null || !storage.getName().equals(name)) {
                if (storage != null)
                    storage.close();
                storage = new PageStorage(name);
            }
            cursor = storage.getPage(link);
            if (!cursor.moveToFirst()) {
                row = new ContentValues();
                row.put(Const.TITLE, title);
                row.put(Const.LINK, link);
                storage.insertTitle(row);
            }
            cursor.close();
        }
        br.close();
        if (storage != null)
            storage.close();
    }

    public void createNotification(String text, String link) {
        if (!link.contains("://"))
            link = Const.SITE + link;
        intent.setData(Uri.parse(link));
        PendingIntent piSummary = PendingIntent.getActivity(App.context, 0, intent, FLAGS);
        PendingIntent piPostpone = notifHelper.getPostponeSummaryNotif(notif_id, text, link);
        notifBuilder = notifHelper.getNotification(
                App.context.getString(R.string.site_name), text,
                NotificationHelper.CHANNEL_SUMMARY);
        notifBuilder.setContentIntent(piSummary)
                .setGroup(NotificationHelper.GROUP_SUMMARY)
                .addAction(0, App.context.getString(R.string.postpone), piPostpone);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            notifBuilder.setFullScreenIntent(piEmpty, true);
    }

    public boolean isNotification() {
        return notifBuilder != null;
    }

    public void muteNotification() {
        notifBuilder.setChannelId(NotificationHelper.CHANNEL_MUTE);
    }

    public void showNotification() {
        notifHelper.notify(notif_id, notifBuilder);
        notif_id++;
    }

    public void groupNotification() {
        notifBuilder = notifHelper.getSummaryNotif(
                App.context.getString(R.string.appeared_new_some),
                NotificationHelper.CHANNEL_SUMMARY);
        intent.setData(Uri.parse(Const.SITE + Const.RSS));
        intent.putExtra(DataBase.ID, notif_id);
        PendingIntent piSummary = PendingIntent.getActivity(App.context, 0, intent, FLAGS);
        notifBuilder.setContentIntent(piSummary)
                .setGroup(NotificationHelper.GROUP_SUMMARY);
        notifBuilder.setFullScreenIntent(piEmpty, true);
    }

    public void singleNotification(String text) {
        notifBuilder.setContentText(App.context.getString(R.string.appeared_new) + text);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            notifBuilder.setFullScreenIntent(piEmpty, true);
    }

    public void setPreferences() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            SharedPreferences pref = App.context.getSharedPreferences(Const.SUMMARY, Context.MODE_PRIVATE);
            boolean sound = pref.getBoolean(SetNotifDialog.SOUND, false);
            boolean vibration = pref.getBoolean(SetNotifDialog.VIBR, true);
            notifBuilder.setLights(Color.GREEN, DateHelper.SEC_IN_MILLS, DateHelper.SEC_IN_MILLS);
            if (sound) {
                String uri = pref.getString(SetNotifDialog.URI, null);
                if (uri == null)
                    notifBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                else
                    notifBuilder.setSound(Uri.parse(uri));
            }
            if (vibration)
                notifBuilder.setVibrate(new long[]{500, 1500});
        }
    }

    public static void postpone(String des, String link) {
        Lib.showToast(App.context.getString(R.string.postpone_alert));
        Intent intent = new Intent(App.context, Rec.class);
        intent.putExtra(Const.DESCTRIPTION, des);
        intent.putExtra(Const.LINK, link);
        PendingIntent piPostpone = PendingIntent.getBroadcast(App.context, 3, intent, FLAGS);
        NotificationHelper.setAlarm(piPostpone, TEN_MIN_IN_MILLS + System.currentTimeMillis());
    }

    public static class Rec extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            SummaryHelper summaryHelper = new SummaryHelper();
            summaryHelper.createNotification(
                    intent.getStringExtra(Const.DESCTRIPTION),
                    intent.getStringExtra(Const.LINK));
            summaryHelper.showNotification();
        }
    }
}
