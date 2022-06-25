package ru.neosvet.vestnewage.utils;

import static android.content.Context.MODE_PRIVATE;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import java.io.File;

import ru.neosvet.vestnewage.App;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.data.DataBase;
import ru.neosvet.vestnewage.data.DateUnit;
import ru.neosvet.vestnewage.data.Section;
import ru.neosvet.vestnewage.helper.BookHelper;
import ru.neosvet.vestnewage.helper.BrowserHelper;
import ru.neosvet.vestnewage.helper.CabinetHelper;
import ru.neosvet.vestnewage.helper.MainHelper;
import ru.neosvet.vestnewage.helper.SearchHelper;
import ru.neosvet.vestnewage.service.LoaderService;
import ru.neosvet.vestnewage.storage.AdsStorage;
import ru.neosvet.vestnewage.view.activity.BrowserActivity;
import ru.neosvet.vestnewage.view.activity.MainActivity;
import ru.neosvet.vestnewage.viewmodel.SiteToiler;

public class LaunchUtils {
    private static final int FLAGS = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ?
            PendingIntent.FLAG_UPDATE_CURRENT :
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;
    private static final String PREF_NAME = "main";
    private SharedPreferences pref = App.context.getSharedPreferences(PREF_NAME, MODE_PRIVATE);
    private final int START_ID = 900;
    private int notif_id = START_ID;
    private static int ver = -1;
    private NotificationUtils notifHelper;
    private final Intent main = new Intent();

    public Intent getIntent() {
        return main;
    }

    public void checkAdapterNewVersion() {
        if (ver == -1)
            ver = getPreviosVer();
        if (ver == 0) {
            notifHelper = new NotificationUtils();
            showNotifTip(App.context.getString(R.string.check_out_settings),
                    App.context.getString(R.string.example_periodic_check), getSettingsIntent());
            new Thread(() -> {
                try {
                    Thread.sleep(10000);
                    showNotifDownloadAll();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
        if (ver < 59) {
            renamePrefs();
            renameBookPref();
            deleteBrowserFiles();
        }
        if (ver > 44 && ver < 47) {
            AdsStorage storage = new AdsStorage();
            storage.delete();
            storage.close();
        }
        if (ver == 0)
            showSummaryNotif();
    }

    private void renameBookPref() {
        SharedPreferences pref = App.context.getSharedPreferences(BookHelper.TAG, MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        int i = pref.getInt("kat", 0);
        editor.remove("kat");
        editor.putInt(Const.POEMS, i);
        i = pref.getInt("pos", 0);
        editor.remove("pos");
        editor.putInt(Const.EPISTLES, i);
        editor.apply();
    }

    private void deleteBrowserFiles() {
        File f = Lib.getFile(Const.DARK);
        if (f.exists()) f.delete();
        f = Lib.getFile(Const.LIGHT);
        if (f.exists()) f.delete();
        f = Lib.getFile("/style/style.css");
        if (f.exists()) f.delete();
        f = Lib.getFile("/page.html");
        if (f.exists()) f.delete();
    }

    private void renamePrefs() {
        String p = "/shared_prefs/", x = ".xml";
        File f = Lib.getFileP(p + "PromHelper.xml");
        if (f.exists())
            f.delete();
        f = Lib.getFileP(p + "MainActivity.xml");
        if (f.exists())
            f.renameTo(Lib.getFileP(p + MainHelper.TAG + x));
        f = Lib.getFileP(p + "BrowserActivity.xml");
        if (f.exists())
            f.renameTo(Lib.getFileP(p + BrowserHelper.TAG + x));
        f = Lib.getFileP(p + "CabmainFragment.xml");
        if (f.exists())
            f.renameTo(Lib.getFileP(p + CabinetHelper.TAG + x));
        f = Lib.getFileP(p + "BookFragment.xml");
        if (f.exists())
            f.renameTo(Lib.getFileP(p + BookHelper.TAG + x));
        f = Lib.getFileP(p + "SearchFragment.xml");
        if (f.exists())
            f.renameTo(Lib.getFileP(p + SearchHelper.TAG + x));
    }

    private void showNotifDownloadAll() {
        Intent intent = new Intent(App.context, LoaderService.class);
        intent.putExtra(Const.MODE, LoaderService.DOWNLOAD_ALL);
        intent.putExtra(Const.TASK, "");
        PendingIntent piStart = PendingIntent.getService(App.context, 0, intent, FLAGS);
        NotificationCompat.Builder notifBuilder = notifHelper.getNotification(
                App.context.getString(R.string.downloads_all_title),
                App.context.getString(R.string.downloads_all_msg),
                NotificationUtils.CHANNEL_TIPS);
        notifBuilder.setContentIntent(piStart);
        notifBuilder.setGroup(NotificationUtils.GROUP_TIPS);
        notifBuilder.setSound(null);
        notifHelper.notify(800, notifBuilder);
    }

    public boolean isNeedLoad() {
        return DateUnit.isLongAgo(pref.getLong(Const.TIME, 0));
    }

    private int getPreviosVer() {
        int prev = pref.getInt("ver", 0);
        try {
            int cur = App.context.getPackageManager().getPackageInfo(App.context.getPackageName(), 0).versionCode;
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
        PendingIntent piStart = PendingIntent.getActivity(App.context, 0, intent, FLAGS);
        NotificationCompat.Builder notifBuilder = notifHelper.getNotification(
                title, msg, NotificationUtils.CHANNEL_TIPS);
        notifBuilder.setContentIntent(piStart);
        notifBuilder.setGroup(NotificationUtils.GROUP_TIPS);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) { // on N with setFullScreenIntent don't work summary group
            PendingIntent piEmpty = PendingIntent.getActivity(App.context, 0, new Intent(), FLAGS);
            notifBuilder.setFullScreenIntent(piEmpty, false);
        }
        notifBuilder.setSound(null);
        notifHelper.notify(++notif_id, notifBuilder);
    }

    private void showSummaryNotif() {
        if (notif_id - START_ID < 2) return; //notifications < 2, summary is not need
        NotificationCompat.Builder notifBuilder = notifHelper.getSummaryNotif(
                App.context.getString(R.string.tips),
                NotificationUtils.CHANNEL_TIPS);
        notifBuilder.setGroup(NotificationUtils.GROUP_TIPS);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            notifBuilder.setContentIntent(PendingIntent.getActivity(App.context, 0,
                    getSettingsIntent(), FLAGS));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            PendingIntent piEmpty = PendingIntent.getActivity(App.context, 0, new Intent(), FLAGS);
            notifBuilder.setFullScreenIntent(piEmpty, false);
        }
        notifHelper.notify(START_ID, notifBuilder);
    }

    private void reInitProm() {
        SharedPreferences pref = App.context.getSharedPreferences(PromUtils.TAG, MODE_PRIVATE);
        PromUtils prom = new PromUtils(null);
        prom.initNotif(pref.getInt(Const.TIME, Const.TURN_OFF));
    }

    public void reInitProm(int timeDiff) {
        SharedPreferences pref = App.context.getSharedPreferences(PromUtils.TAG, MODE_PRIVATE);
        if (timeDiff != pref.getInt(Const.TIMEDIFF, 0)) {
            SharedPreferences.Editor editor = pref.edit();
            editor.putInt(Const.TIMEDIFF, timeDiff);
            editor.apply();

            PromUtils prom = new PromUtils(null);
            prom.initNotif(timeDiff);
        }
    }

    private Intent getSettingsIntent() {
        Intent intent = new Intent(App.context, MainActivity.class);
        intent.putExtra(Const.CUR_ID, Section.SETTINGS.toString());
        return intent;
    }

    public boolean openLink(Intent intent) {
        if (intent.getBooleanExtra(Const.ADS, false)) {
            main.putExtra(Const.CUR_ID, Section.SITE.toString());
            main.putExtra(Const.TAB, 2);
            return true;
        }

        Uri data = intent.getData();
        if (data == null)
            return false;

        String link = data.getPath();
        if (link == null)
            return false;

        if (link.contains(Const.RSS)) {
            main.putExtra(Const.CUR_ID, Section.SUMMARY.toString());
            if (intent.hasExtra(DataBase.ID))
                main.putExtra(DataBase.ID, intent.getIntExtra(DataBase.ID,
                        NotificationUtils.NOTIF_SUMMARY));
        } else if (link.length() < 2 || link.equals("/index.html")) {
            main.putExtra(Const.CUR_ID, Section.SITE.toString());
            main.putExtra(Const.TAB, 0);
        } else if (link.equals(SiteToiler.NOVOSTI)) {
            main.putExtra(Const.CUR_ID, Section.SITE.toString());
            main.putExtra(Const.TAB, 1);
        } else if (link.contains(Const.HTML)) {
            BrowserActivity.openReader(link.substring(1), null);
        } else if (data.getQuery() != null && data.getQuery().contains("date")) { //http://blagayavest.info/poems/?date=11-3-2017
            String s = data.getQuery().substring(5);
            String m = s.substring(s.indexOf("-") + 1, s.lastIndexOf("-"));
            link = link.substring(1) + s.substring(0, s.indexOf("-"))
                    + "." + (m.length() == 1 ? "0" : "") + m
                    + "." + s.substring(s.lastIndexOf("-") + 3) + Const.HTML;
            BrowserActivity.openReader(link, null);
        } else if (link.contains("/poems")) {
            main.putExtra(Const.CUR_ID, Section.BOOK.toString());
            main.putExtra(Const.TAB, 0);
        } else if (link.contains("/tolkovaniya") || link.contains("/2016")) {
            main.putExtra(Const.CUR_ID, Section.BOOK.toString());
            main.putExtra(Const.TAB, 1);
        }
        return true;
    }

    public void updateTime() {
        SharedPreferences.Editor editor = pref.edit();
        editor.putLong(Const.TIME, System.currentTimeMillis());
        editor.apply();
    }
}
