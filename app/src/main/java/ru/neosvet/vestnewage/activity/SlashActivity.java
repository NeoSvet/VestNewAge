package ru.neosvet.vestnewage.activity;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import java.io.File;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import ru.neosvet.ui.StatusBar;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.Prom;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.fragment.CalendarFragment;
import ru.neosvet.vestnewage.task.CalendarTask;

public class SlashActivity extends AppCompatActivity {
    private Intent main;
    private StatusBar status;
    private boolean boolAnim;
    private CalendarTask task = null;
    public Lib lib;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.slash_activity);
        main = new Intent(getApplicationContext(), MainActivity.class);
        status = new StatusBar(this, findViewById(R.id.pStatus));
        lib = new Lib(this);
        Uri data = getIntent().getData();
        if (data == null)
            initTask(savedInstanceState);
        else
            boolAnim = true;
        initAnimation();
        if (data != null)
            parseUri(data);

        adapterNewVersion();

        Prom prom = new Prom(this);
        prom.synchronTime();
    }

    private void adapterNewVersion() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //from old version (code 8 and below)
                    SharedPreferences pref = getSharedPreferences(Const.COOKIE, MODE_PRIVATE);
                    if(!pref.getString(Const.NOREAD, "").equals("")) {
                        SharedPreferences.Editor editor = pref.edit();
                        editor.putString(Const.NOREAD, "");
                        editor.apply();
                        File file = new File(getFilesDir() + File.separator + Const.NOREAD);
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
                            int iPlace = cursor.getColumnIndex(DataBase.PLACE);
                            int iTitle = cursor.getColumnIndex(DataBase.TITLE);
                            // первую подборку - "вне подборок" вставлять не надо, надо лишь обновить:
                            ContentValues cv = new ContentValues();
                            cv.put(DataBase.MARKERS, cursor.getString(iMarker));
                            dbM.update(DataBase.COLLECTIONS, cv, DataBase.ID + DataBase.Q,
                                    new String[]{"1"});
                            // остальные вставляем:
                            while (cursor.moveToNext()) {
                                cv = new ContentValues();
                                cv.put(DataBase.PLACE, cursor.getInt(iPlace));
                                cv.put(DataBase.MARKERS, cursor.getString(iMarker));
                                cv.put(DataBase.TITLE, cursor.getString(iTitle));
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
//            Lib.LOG("link1=" + link);
            ////http://blagayavest.info/poems/?date=11-3-2017
            if (link.contains("/rss")) {
                main.putExtra(MainActivity.CUR_ID, R.id.nav_rss);
            } else if (link.length() < 2 || link.equals("/index.html")) {
                main.putExtra(MainActivity.CUR_ID, R.id.nav_main);
                main.putExtra(MainActivity.TAB, 0);
            } else if (link.equals("/novosti.html")) {
                main.putExtra(MainActivity.CUR_ID, R.id.nav_main);
                main.putExtra(MainActivity.TAB, 1);
            } else if (link.equals("/media.html")) {
                main.putExtra(MainActivity.CUR_ID, R.id.nav_main);
                main.putExtra(MainActivity.TAB, 2);
            } else if (link.contains("html")) {
                BrowserActivity.openReader(this, link.substring(1), null);
            } else if (data.getQuery() != null && data.getQuery().contains("date")) {
                String s = data.getQuery().substring(5);
                String m = s.substring(s.indexOf("-") + 1, s.lastIndexOf("-"));
                link = link.substring(1) + s.substring(0, s.indexOf("-"))
                        + "." + (m.length() == 1 ? "0" : "") + m
                        + "." + s.substring(s.lastIndexOf("-") + 3) + Const.HTML;
                BrowserActivity.openReader(this, link, null);
            } else if (link.contains("/poems")) {
                main.putExtra(MainActivity.CUR_ID, R.id.nav_book);
                main.putExtra(MainActivity.TAB, 0);
            } else if (link.contains("/tolkovaniya") || link.contains("/2016")) {
                main.putExtra(MainActivity.CUR_ID, R.id.nav_book);
                main.putExtra(MainActivity.TAB, 1);
            } else if (link.contains("/search")) { //http://blagayavest.info/search/?query=любовь&where=0&start=2
                link = data.getQuery();
                int page = 1;
                if (link.contains("start")) {
                    page = Integer.parseInt(link.substring(link.lastIndexOf("=") + 1));
                    link = link.substring(0, link.lastIndexOf("&"));
                }
                int mode = 5;
                if (link.contains("where")) {
                    Integer.parseInt(link.substring(link.lastIndexOf("=") + 1));
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
                main.putExtra(DataBase.PLACE, page);
                main.putExtra(MainActivity.TAB, mode);
                main.putExtra(MainActivity.CUR_ID, R.id.nav_search);
                main.putExtra(DataBase.LINK, link.substring(link.indexOf("=") + 1));
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(Const.TASK, task);
        super.onSaveInstanceState(outState);
    }

    private void initTask(Bundle state) {
        if (state != null) {
            boolAnim = false;
            task = (CalendarTask) state.getSerializable(Const.TASK);
            if (task != null) {
                task.setAct(this);
                setStatus();
            }
            return;
        }
        boolAnim = true;
        Date dCurrent = new Date();
        File file = new File(getFilesDir() + CalendarFragment.FOLDER +
                dCurrent.getMonth() + "." + dCurrent.getYear());
        if (file.exists())
            if (System.currentTimeMillis() - file.lastModified() > 3600000) {
                task = new CalendarTask(this);
                Date d = new Date();
                task.execute(d.getYear(), d.getMonth(), 1);
            }
    }

    public void finishLoad() {
        main.putExtra(MainActivity.CUR_ID, R.id.nav_calendar);
        task = null;
        if (!boolAnim) {
            startActivity(main);
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (task != null)
            task.cancel(true);
        startActivity(main);
        finish();
    }

    private void initAnimation() {
        if (!boolAnim)
            return;
        Animation anStar = AnimationUtils.loadAnimation(this, R.anim.flash);
        anStar.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                if (task == null) {
                    startActivity(main);
                    finish();
                } else {
                    setStatus();
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        View ivStar = findViewById(R.id.ivStar);
        ivStar.startAnimation(anStar);
    }

    private void setStatus() {
        boolAnim = false;
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

//    @Override
//    protected void onNewIntent(Intent intent) {
//        super.onNewIntent(intent);
//        Uri data = intent.getData();
//        if(data !=null) {
//            Lib lib = new Lib(this);
//            lib.copyAddress("link2=" + data.toString());
//            BrowserActivity.openActivity(this,data.getPath(),false);
//        }
//        Intent main = new Intent(getApplicationContext(), MainActivity.class);
//        startActivity(main);
//    }
}
