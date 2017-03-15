package ru.neosvet.vestnewage;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import java.net.URLDecoder;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import ru.neosvet.ui.StatusBar;
import ru.neosvet.utils.CalendarTask;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;

public class SlashActivity extends AppCompatActivity {
    private Intent main;
    private StatusBar status;
    private boolean boolAnim;
    private CalendarTask task = null;
    private int iNew = 0;
    public Lib lib;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        // удаление таблицы журнала старого образца:
        DataBase dbJournal = new DataBase(this, DataBase.JOURNAL);
        SQLiteDatabase db = dbJournal.getWritableDatabase();
        // сначала проверяем, что таблица существует
        Cursor cursor = db.rawQuery("SELECT * FROM sqlite_master WHERE type='table' AND name"
                + DataBase.Q, new String[]{DataBase.JOURNAL});
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                if (cursor.moveToFirst()) { // таблица существует
                    cursor.close();
                    // теперь проверяем, что наличие столбца ID
                    cursor = db.query(DataBase.JOURNAL, null, null, null, null, null, null);
                    boolean b = true;
                    if (cursor.moveToFirst()) {
                        b = cursor.getColumnIndex(DataBase.ID) == -1; // будет -1, если столбец отсутствует
                    }
                    if (b) { // true, если таблица пустая или если отсутствует столбце ID
                        db.execSQL("drop table " + DataBase.JOURNAL); // удаляем таблицу старого образца
                        //создаем таблицу нового образца:
                        db.execSQL("create table if not exists " + DataBase.JOURNAL + " ("
                                + DataBase.ID + " text primary key,"
                                + DataBase.TIME + " integer);");
                    }
                }
            }
            cursor.close();
        }
    }

    private void parseUri(Uri data) {
        String link;
        if (data.getHost().contains("vk.com")) {
            link = data.getQuery().substring(3);
            if (!link.contains(Lib.SITE)) {
                lib.openInApps(link, null);
                finish();
                return;
            }
            link = link.substring(Lib.SITE.length() - 1);
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
                BrowserActivity.openReader(this, link.substring(1), "");
            } else if (data.getQuery() != null && data.getQuery().contains("date")) {
                String s = data.getQuery().substring(5);
                String m = s.substring(s.indexOf("-") + 1, s.lastIndexOf("-"));
                link = link.substring(1) + s.substring(0, s.indexOf("-"))
                        + "." + (m.length() == 1 ? "0" : "") + m
                        + "." + s.substring(s.lastIndexOf("-") + 3) + ".html";
                BrowserActivity.openReader(this, link, "");
            } else if (link.contains("/poems")) {
                main.putExtra(MainActivity.CUR_ID, R.id.nav_book);
                main.putExtra(MainActivity.TAB, 0);
            } else if (link.contains("/tolkovaniya") || link.contains("/2016")) {
                main.putExtra(MainActivity.CUR_ID, R.id.nav_book);
                main.putExtra(MainActivity.TAB, 1);
            } else if (link.contains("/search")) {
                //http://blagayavest.info/search/?query=любовь&where=0
               /* <option selected="" value="0">в Посланиях</option>
                <option value="5">в Катренах</option>
                <option value="1">в заголовках</option>
                <option value="2">по всему Сайту</option>
                <option value="3">по дате</option>
                <!-- <option  value="4">в цитатах</option> -->*/
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(Lib.TASK, task);
        outState.putInt(MainActivity.TAB, iNew);
        super.onSaveInstanceState(outState);
    }

    private void initTask(Bundle state) {
        if (state != null) {
            boolAnim = false;
            iNew = state.getInt(MainActivity.TAB, 0);
            task = (CalendarTask) state.getSerializable(Lib.TASK);
            if (task != null) {
                task.setAct(this);
                setStatus();
            }
            return;
        }
        boolAnim = true;
        if (System.currentTimeMillis() - lib.getTimeLastVisit() > 3600000) {
            try {
                String s = lib.getCookies(true);
                //a:3:{i:0;a:2:{s:2:
                if (s.length() > 10) {
                    s = URLDecoder.decode(s, "UTF-8").substring(2);
                    s = s.substring(0, s.indexOf(":"));
                    iNew = Integer.parseInt(s);
                }
            } catch (Exception ex) {
            }
            task = new CalendarTask(this);
            Date d = new Date();
            task.execute(d.getYear(), d.getMonth(), 1);
        }
    }

    public void finishLoad() {
        main.putExtra(MainActivity.CUR_ID, R.id.nav_calendar);
        main.putExtra(MainActivity.TAB, iNew + 1);
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
