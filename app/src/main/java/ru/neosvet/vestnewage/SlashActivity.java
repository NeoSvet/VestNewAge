package ru.neosvet.vestnewage;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import java.net.URLDecoder;
import java.util.Date;

import ru.neosvet.ui.StatusBar;
import ru.neosvet.utils.CalendarTask;
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
                BrowserActivity.openPage(this, link.substring(1), "");
            } else if (data.getQuery() != null && data.getQuery().contains("date")) {
                String s = data.getQuery().substring(5);
                String m = s.substring(s.indexOf("-") + 1, s.lastIndexOf("-"));
                link = link.substring(1) + s.substring(0, s.indexOf("-"))
                        + "." + (m.length() == 1 ? "0" : "") + m
                        + "." + s.substring(s.lastIndexOf("-") + 3) + ".html";
                BrowserActivity.openPage(this, link, "");
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
        View ivStart = findViewById(R.id.ivStart);
        ivStart.startAnimation(anStar);
    }

    private void setStatus() {
        boolAnim = false;
        status.setClick(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });
        status.setLoad(true);
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
