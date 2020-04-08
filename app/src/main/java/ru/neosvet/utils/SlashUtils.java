package ru.neosvet.utils;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.NotificationCompat;

import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.BrowserActivity;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.fragment.SiteFragment;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.NotificationHelper;
import ru.neosvet.vestnewage.helpers.PromHelper;

import static android.content.Context.MODE_PRIVATE;

public class SlashUtils {
    private final String SETTINGS = "main";
    private final int START_ID = 900;
    private int notif_id = START_ID;
    private NotificationHelper notifHelper;
    private Intent main;
    private Context context;

    public SlashUtils(Context context) {
        this.context = context;
        main = new Intent();
    }

    public Intent getIntent() {
        return main;
    }

    public void checkAdapterNewVersion() {
        int ver = getPreviosVer();
        notifHelper = new NotificationHelper(context);
        if (ver < 21) {
            SharedPreferences pref = context.getSharedPreferences(Const.SUMMARY, MODE_PRIVATE);
            int p = pref.getInt(Const.TIME, Const.TURN_OFF);
            if (p == Const.TURN_OFF)
                showNotifTip(context.getResources().getString(R.string.are_you_know),
                        context.getResources().getString(R.string.new_option_notif), getSettingsIntent());
            else {
                pref = context.getSharedPreferences(Const.PROM, MODE_PRIVATE);
                p = pref.getInt(Const.TIME, Const.TURN_OFF);
                if (p == Const.TURN_OFF)
                    showNotifTip(context.getResources().getString(R.string.are_you_know),
                            context.getResources().getString(R.string.new_option_notif), getSettingsIntent());
            }
        }
        if (ver == 0) {
            showSummaryNotif();
            return;
        }
    }

    public boolean isNeedLoad() {
        SharedPreferences pref = context.getSharedPreferences(SETTINGS, MODE_PRIVATE);
        long time = pref.getLong(Const.TIME, 0);
        if (System.currentTimeMillis() - time > DateHelper.HOUR_IN_MILLS) {
            SharedPreferences.Editor editor = pref.edit();
            editor.putLong(Const.TIME, System.currentTimeMillis());
            editor.apply();
            return true;
        }
        return false;
    }

    private int getPreviosVer() {
        SharedPreferences pref = context.getSharedPreferences(SETTINGS, MODE_PRIVATE);
        int prev = pref.getInt("ver", 0);
        try {
            int cur = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
            if (prev < cur) {
                SharedPreferences.Editor editor = pref.edit();
                editor.putInt("ver", cur);
                editor.apply();
                reInitProm();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return prev;
    }

    private void showNotifTip(String title, String msg, Intent intent) {
        PendingIntent piStart = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder notifBuilder = notifHelper.getNotification(
                title, msg, NotificationHelper.CHANNEL_TIPS);
        notifBuilder.setContentIntent(piStart);
        notifBuilder.setGroup(NotificationHelper.GROUP_TIPS);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) { // on N with setFullScreenIntent don't work summary group
            PendingIntent piEmpty = PendingIntent.getActivity(context, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
            notifBuilder.setFullScreenIntent(piEmpty, false);
        }
        notifBuilder.setSound(null);
        notifHelper.notify(++notif_id, notifBuilder);
    }

    private void showSummaryNotif() {
        if (notif_id - START_ID < 2) return; //notifications < 2, summary is not need
        NotificationCompat.Builder notifBuilder = notifHelper.getSummaryNotif(
                context.getResources().getString(R.string.tips),
                NotificationHelper.CHANNEL_TIPS);
        notifBuilder.setGroup(NotificationHelper.GROUP_TIPS);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            notifBuilder.setContentIntent(PendingIntent.getActivity(context, 0,
                    getSettingsIntent(), PendingIntent.FLAG_UPDATE_CURRENT));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            PendingIntent piEmpty = PendingIntent.getActivity(context, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
            notifBuilder.setFullScreenIntent(piEmpty, false);
        }
        notifHelper.notify(START_ID, notifBuilder);
    }

    public void reInitProm() {
        SharedPreferences pref = context.getSharedPreferences(Const.PROM, MODE_PRIVATE);
        PromHelper prom = new PromHelper(context, null);
        prom.initNotif(pref.getInt(Const.TIME, Const.TURN_OFF));
    }

    private Intent getSettingsIntent() {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(Const.CUR_ID, R.id.nav_settings);
        return intent;
    }

    public boolean openLink(Intent intent) {
        Uri data = intent.getData();
        if (data == null)
            return false;

        String link = data.getPath();
        if (link == null)
            return false;

        if (link.contains(Const.RSS)) {
            main.putExtra(Const.CUR_ID, R.id.nav_rss);
            if (intent.hasExtra(DataBase.ID))
                main.putExtra(DataBase.ID, intent.getIntExtra(DataBase.ID,
                        NotificationHelper.NOTIF_SUMMARY));
        } else if (link.length() < 2 || link.equals("/index.html")) {
            main.putExtra(Const.CUR_ID, R.id.nav_site);
            main.putExtra(Const.TAB, 0);
        } else if (link.equals(SiteFragment.NOVOSTI)) {

            main.putExtra(Const.CUR_ID, R.id.nav_site);
            main.putExtra(Const.TAB, 1);
        } else if (link.contains(Const.HTML)) {
            BrowserActivity.openReader(context, link.substring(1), null);
        } else if (data.getQuery() != null && data.getQuery().contains("date")) { //http://blagayavest.info/poems/?date=11-3-2017
            String s = data.getQuery().substring(5);
            String m = s.substring(s.indexOf("-") + 1, s.lastIndexOf("-"));
            link = link.substring(1) + s.substring(0, s.indexOf("-"))
                    + "." + (m.length() == 1 ? "0" : "") + m
                    + "." + s.substring(s.lastIndexOf("-") + 3) + Const.HTML;
            BrowserActivity.openReader(context, link, null);
        } else if (link.contains("/poems")) {
            main.putExtra(Const.CUR_ID, R.id.nav_book);
            main.putExtra(Const.TAB, 0);
        } else if (link.contains("/tolkovaniya") || link.contains("/2016")) {
            main.putExtra(Const.CUR_ID, R.id.nav_book);
            main.putExtra(Const.TAB, 1);
        } else if (link.contains("/search")) { //http://blagayavest.info/search/?query=любовь&where=0&start=2
            link = data.getQuery();
            int page = 1;
            if (link.contains("start")) {
                page = Integer.parseInt(link.substring(link.lastIndexOf("=") + 1));
                link = link.substring(0, link.lastIndexOf("&"));
            }
            int mode = 5;
            if (link.contains("where")) {
                mode = Integer.parseInt(link.substring(link.lastIndexOf("=") + 1));
                link = link.substring(0, link.lastIndexOf("&"));
                    /* <option selected="" value="0">в Посланиях</option> 0
                       <option value="5">в Катренах</option> 1
                       <option value="1">в заголовках</option> 2
                       <option value="2">по всему Сайту</option> 3
                       <option value="3">по дате</option> 4
                       <!-- <option  value="4">в цитатах</option> -->*/
                if (mode == 5) mode = 1; // в Катренах - на сайте 5, здесь - 1
                else if (mode > 0) mode++; // поэтому остальное смещается
            }
            main.putExtra(Const.PLACE, page);
            main.putExtra(Const.TAB, mode);
            main.putExtra(Const.CUR_ID, R.id.nav_search);
            main.putExtra(Const.LINK, link.substring(link.indexOf("=") + 1));
        }
        return true;
    }
}
