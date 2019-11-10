package ru.neosvet.vestnewage.activity;

import android.app.PendingIntent;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.work.Data;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

import ru.neosvet.ui.StatusButton;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.NotificationHelper;
import ru.neosvet.vestnewage.helpers.PromHelper;
import ru.neosvet.vestnewage.helpers.UnreadHelper;
import ru.neosvet.vestnewage.model.SlashModel;

public class SlashActivity extends AppCompatActivity {
    private final int START_ID = 900;
    private int notif_id = START_ID;
    private Intent main;
    private StatusButton status;
    private boolean anim = true;
    private Lib lib;
    private SlashModel model;
    private NotificationHelper notifHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.slash_activity);
        main = new Intent(getApplicationContext(), MainActivity.class);
        status = new StatusButton(this, findViewById(R.id.pStatus));
        lib = new Lib(this);

        initAnimation();
        int ver = lib.getPreviosVer();
        if (ver < 32)
            adapterNewVersion32();
        if (initData()) {
            if (main != null)
                startActivity(main);
            finish();
            return;
        }

        notifHelper = new NotificationHelper(SlashActivity.this);
        if (ver < 21) {
            SharedPreferences pref = getSharedPreferences(Const.SUMMARY, MODE_PRIVATE);
            int p = pref.getInt(Const.TIME, Const.TURN_OFF);
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(Const.CUR_ID, R.id.nav_settings);
            if (p == Const.TURN_OFF)
                showNotifTip(getResources().getString(R.string.are_you_know),
                        getResources().getString(R.string.new_option_notif), intent);
            else {
                pref = getSharedPreferences(Const.PROM, MODE_PRIVATE);
                p = pref.getInt(Const.TIME, Const.TURN_OFF);
                if (p == Const.TURN_OFF)
                    showNotifTip(getResources().getString(R.string.are_you_know),
                            getResources().getString(R.string.new_option_notif), intent);
            }
        }
        if (ver == 0) {
            showSummaryNotif();
            return;
        }
        if (ver < 10)
            adapterNewVersion10();
//        if (ver < 19)
//            rebuildNotif();
        if (ver < 21)
            adapterNewVersion21();
        if (ver < 31) {
            PromHelper prom = new PromHelper(this, null);
            prom.clearTimeDiff();
        }

        showSummaryNotif();
    }

    @Override
    public void onPause() {
        super.onPause();
        model.removeObserves(this);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (model.inProgress)
            model.finish(this);
        startActivity(main);
        finish();
    }

    private boolean initData() {
        Uri data = getIntent().getData();
        if (data == null)
            initModel();
        else
            parseUri(data);
        return data != null;
    }

    private void parseUri(Uri data) {
        String link;
        if (data.getHost().contains("vk.com")) {
            link = data.getQuery().substring(3);
            if (!link.contains(Const.SITE)) {
                lib.openInApps(link, null);
                finish();
                return;
            }
            link = link.substring(Const.SITE.length() - 1);
        } else
            link = data.getPath();
        if (link != null) {
            if (link.contains(Const.RSS)) {
                main.putExtra(Const.CUR_ID, R.id.nav_rss);
                if (getIntent().hasExtra(DataBase.ID))
                    main.putExtra(DataBase.ID, getIntent().getIntExtra(DataBase.ID, NotificationHelper.NOTIF_SUMMARY));
            } else if (link.length() < 2 || link.equals("/index.html")) {
                main.putExtra(Const.CUR_ID, R.id.nav_main);
                main.putExtra(Const.TAB, 0);
            } else if (link.equals("/novosti.html")) {
                main.putExtra(Const.CUR_ID, R.id.nav_main);
                main.putExtra(Const.TAB, 1);
            } else if (link.equals("/media.html")) {
                main.putExtra(Const.CUR_ID, R.id.nav_main);
                main.putExtra(Const.TAB, 2);
            } else if (link.contains("html")) {
                BrowserActivity.openReader(this, link.substring(1), null);
                main = null;
            } else if (data.getQuery() != null && data.getQuery().contains("date")) { //http://blagayavest.info/poems/?date=11-3-2017
                String s = data.getQuery().substring(5);
                String m = s.substring(s.indexOf("-") + 1, s.lastIndexOf("-"));
                link = link.substring(1) + s.substring(0, s.indexOf("-"))
                        + "." + (m.length() == 1 ? "0" : "") + m
                        + "." + s.substring(s.lastIndexOf("-") + 3) + Const.HTML;
                BrowserActivity.openReader(this, link, null);
                main = null;
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
        }
    }

    private void initModel() {
        model = ViewModelProviders.of(this).get(SlashModel.class);
        model.getProgress().observe(this, new Observer<Data>() {
            @Override
            public void onChanged(@Nullable Data data) {
                if (data.getBoolean(Const.TIME, false)) {
                    //TODO rebuild prom notif
                }
                if (data.getBoolean(Const.LIST, false)) {
                    finishLoad();
                }
            }
        });
        if (model.inProgress) {
            setStatus();
            return;
        }
        DateHelper date = DateHelper.initToday(SlashActivity.this);
        DataBase dataBase = new DataBase(SlashActivity.this, date.getMY());
        SQLiteDatabase db = dataBase.getWritableDatabase();
        Cursor cursor = db.query(Const.TITLE, null, null, null, null, null, null);
        long time = 0;
        if (cursor.moveToFirst())
            time = cursor.getLong(cursor.getColumnIndex(Const.TIME));
        cursor.close();
        dataBase.close();
        if (System.currentTimeMillis() - time < DateHelper.HOUR_IN_MILLS)
            return;
        SharedPreferences pref = getSharedPreferences(MainActivity.class.getSimpleName(), Context.MODE_PRIVATE);
        model.startLoad(pref.getInt(Const.START_SCEEN, Const.SCREEN_CALENDAR) == Const.SCREEN_SUMMARY,
                date.getMonth(), date.getYear());
    }

    public void finishLoad() {
        model.finish(this);
        if (!anim) {
            startActivity(main);
            finish();
        }
    }

    private void initAnimation() {
        Animation anStar = AnimationUtils.loadAnimation(this, R.anim.flash);
        anStar.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                anim = false;
                if (!model.inProgress) {
                    startActivity(main);
                    finish();
                } else
                    setStatus();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        View ivStar = findViewById(R.id.ivStar);
        ivStar.startAnimation(anStar);
    }

    private void setStatus() {
        status.setClick(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });
        //костыль, чтобы крутилось нормально:
        findViewById(R.id.pStatus).setVisibility(View.VISIBLE);
        final Handler hStatus = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                status.setLoad(true);
                return false;
            }
        });
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                hStatus.sendEmptyMessage(0);
            }
        }, 10);

    }

    private void showNotifTip(String title, String msg, Intent intent) {
        PendingIntent piStart = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder notifBuilder = notifHelper.getNotification(
                title, msg, NotificationHelper.CHANNEL_TIPS);
        notifBuilder.setContentIntent(piStart);
        notifBuilder.setGroup(NotificationHelper.GROUP_TIPS);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) { // on N with setFullScreenIntent don't work summary group
            PendingIntent piEmpty = PendingIntent.getActivity(this, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
            notifBuilder.setFullScreenIntent(piEmpty, false);
        }
        notifBuilder.setSound(null);
        notifHelper.notify(++notif_id, notifBuilder);
    }

   /* private void rebuildNotif() {
        SharedPreferences pref = getSharedPreferences(Const.SUMMARY, MODE_PRIVATE);
        int p = pref.getInt(Const.TIME, Const.TURN_OFF);
        if (p != Const.TURN_OFF)
            SummaryHelper.setReceiver(this, p);
        pref = getSharedPreferences(Const.PROM, MODE_PRIVATE);
        p = pref.getInt(Const.TIME, Const.TURN_OFF);
        if (p != Const.TURN_OFF)
            PromReceiver.setReceiver(this, p);
    }*/

    private void showSummaryNotif() {
        if (notif_id - START_ID < 2) return; //notifications < 2, summary is not need
        NotificationCompat.Builder notifBuilder = notifHelper.getSummaryNotif(
                getResources().getString(R.string.tips),
                NotificationHelper.CHANNEL_TIPS);
        notifBuilder.setGroup(NotificationHelper.GROUP_TIPS);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            notifBuilder.setContentIntent(PendingIntent.getActivity(this, 0,
                    getSettingsIntent(), PendingIntent.FLAG_UPDATE_CURRENT));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            PendingIntent piEmpty = PendingIntent.getActivity(this, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
            notifBuilder.setFullScreenIntent(piEmpty, false);
        }
        notifHelper.notify(START_ID, notifBuilder);
    }

    private void adapterNewVersion32() {
        File f = new File("/data/data/" + this.getPackageName() + "/shared_prefs/activity."
                + MainActivity.class.getSimpleName() + ".xml");
        if (f.exists())
            f.renameTo(new File(f.getParent() + "/" + MainActivity.class.getSimpleName() + ".xml"));
        f = new File(f.toString().replace(MainActivity.class.getSimpleName(), BrowserActivity.class.getSimpleName()));
        if (f.exists())
            f.renameTo(new File(f.getParent() + "/" + BrowserActivity.class.getSimpleName() + ".xml"));
        SharedPreferences pref = getSharedPreferences(MainActivity.class.getSimpleName(), Context.MODE_PRIVATE);
        if (pref.getBoolean("menu_mode", false)) {
            SharedPreferences.Editor editor = pref.edit();
            editor.putInt(Const.START_SCEEN, Const.SCREEN_MENU);
            editor.apply();
        }
    }

    private void adapterNewVersion21() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File folder = new File(getFilesDir() + "/calendar/");
                    if (folder.exists()) {
                        for (File file : folder.listFiles())
                            file.delete();
                        folder.delete();
                    }
                } catch (Exception e) {
                }
            }
        }).start();
    }

    private void adapterNewVersion10() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File file = new File(getFilesDir() + File.separator + UnreadHelper.NAME);
                    if (file.exists())
                        file.delete();
                    //from old version (code 8 and below)
                    SharedPreferences pref = getSharedPreferences(Const.COOKIE, MODE_PRIVATE);
                    if (!pref.getString(UnreadHelper.NAME, "").equals("")) {
                        SharedPreferences.Editor editor = pref.edit();
                        editor.putString(UnreadHelper.NAME, "");
                        editor.apply();
                        file = new File(getFilesDir() + File.separator + UnreadHelper.NAME);
                        if (file.exists())
                            file.delete();
                    }
                    // удаляем материалы в виде страниц:
                    File dir = getFilesDir();
                    if ((new File(dir + "/poems")).exists()) {
                        for (File d : dir.listFiles()) {
                            if (d.isDirectory() && !d.getName().equals("instant-run")) {
                                // instant-run не трогаем, т.к. это системная папка
                                if (d.listFiles() != null) // если папка не пуста
                                    for (File f : d.listFiles())
                                        f.delete();
                                d.delete();
                            }
                        }
                        // перенос таблицы подборок в базу закладок
                        DataBase dbCol = new DataBase(SlashActivity.this, DataBase.COLLECTIONS);
                        SQLiteDatabase dbC = dbCol.getWritableDatabase();
                        DataBase dbMar = new DataBase(SlashActivity.this, DataBase.MARKERS);
                        SQLiteDatabase dbM = dbMar.getWritableDatabase();
                        Cursor cursor = dbC.query(DataBase.COLLECTIONS, null, null, null, null, null, null);
                        if (cursor.moveToFirst()) {
                            int iMarker = cursor.getColumnIndex(DataBase.MARKERS);
                            int iPlace = cursor.getColumnIndex(Const.PLACE);
                            int iTitle = cursor.getColumnIndex(Const.TITLE);
                            // первую подборку - "вне подборок" вставлять не надо, надо лишь обновить:
                            ContentValues cv = new ContentValues();
                            cv.put(DataBase.MARKERS, cursor.getString(iMarker));
                            dbM.update(DataBase.COLLECTIONS, cv, DataBase.ID + DataBase.Q,
                                    new String[]{"1"});
                            // остальные вставляем:
                            while (cursor.moveToNext()) {
                                cv = new ContentValues();
                                cv.put(Const.PLACE, cursor.getInt(iPlace));
                                cv.put(DataBase.MARKERS, cursor.getString(iMarker));
                                cv.put(Const.TITLE, cursor.getString(iTitle));
                                dbM.insert(DataBase.COLLECTIONS, null, cv);
                            }
                        }
                        cursor.close();
                        dbMar.close();
                        // удаление базы журнала старого образца и базы подборок:
                        dir = lib.getDBFolder();
                        for (File f : dir.listFiles()) {
                            if (f.getName().contains(DataBase.COLLECTIONS) ||
                                    f.getName().indexOf(DataBase.JOURNAL) == 0)
                                f.delete();
                        }
                    }
                } catch (Exception e) {
                }
            }
        }).start();
    }

    public Intent getSettingsIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(Const.CUR_ID, R.id.nav_settings);
        return intent;
    }
}
