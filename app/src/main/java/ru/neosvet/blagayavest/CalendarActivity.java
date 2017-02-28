package ru.neosvet.blagayavest;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ru.neosvet.ui.CalendarAdapter;
import ru.neosvet.ui.CalendarItem;
import ru.neosvet.ui.ListAdapter;
import ru.neosvet.ui.ListItem;
import ru.neosvet.ui.MyActivity;
import ru.neosvet.ui.RecyclerItemClickListener;
import ru.neosvet.ui.ResizeAnim;
import ru.neosvet.ui.StatusBar;
import ru.neosvet.utils.CalendarTask;
import ru.neosvet.utils.Lib;


public class CalendarActivity extends MyActivity {
    public static final String CURRENT_DATE = "current_date", CALENDAR = "/calendar/";
    private int today_m, today_y;
    private CalendarAdapter adCalendar;
    private RecyclerView rvCalendar;
    private int x, y;
    private boolean boolNotClick = false;
    private NavigationView navigationView;
    private Date dCurrent;
    private StatusBar status;
    private TextView tvDate, tvNew;
    private ListView lvNoread;
    private View pCalendar, ivPrev, ivNext, fabRefresh, fabClose, fabClear;
    private CalendarTask task = null;
    private ListAdapter adNoread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);
        initViews();
        setViews();
        initCalendar();
        restoreActivityState(savedInstanceState);

        SharedPreferences pref = getSharedPreferences("set", MODE_PRIVATE);
        if (pref.getBoolean("first", true)) {
            SharedPreferences.Editor editor = pref.edit();
            editor.putBoolean("first", false);
            editor.apply();
            Intent intent = new Intent(this, HelpActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(Lib.NOREAD, (fabClose.getVisibility() == View.VISIBLE));
        outState.putLong(CURRENT_DATE, dCurrent.getTime());
        outState.putSerializable(Lib.TASK, task);
        super.onSaveInstanceState(outState);
    }

    protected void restoreActivityState(Bundle state) {
        super.restoreActivityState(state);
        if (state == null) {
            Date d = new Date();
            dCurrent = new Date(d.getYear(), d.getMonth(), d.getDate());
        } else {
            dCurrent = new Date(state.getLong(CURRENT_DATE));
            task = (CalendarTask) state.getSerializable(Lib.TASK);
            if (task != null) {
                setStatus(true);
                task.setAct(this);
            }
            if (state.getBoolean(Lib.NOREAD, false)) {
                pCalendar.setVisibility(View.GONE);
                tvNew.setVisibility(View.GONE);
                fabRefresh.setVisibility(View.GONE);
                fabClear.setVisibility(View.VISIBLE);
                fabClose.setVisibility(View.VISIBLE);
                lvNoread.getLayoutParams().height = (int) (getResources().getInteger(R.integer.height_list)
                        * getResources().getDisplayMetrics().density);
                lvNoread.requestLayout();
                lvNoread.setVisibility(View.VISIBLE);
            }
        }
        createCalendar(0);
    }

    private void setViews() {
        tvNew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (tvNew.getText().toString().equals("0"))
                    return;
                if (tvNew.getText().toString().equals("..."))
                    adNoread.addItem(new ListItem(getResources().getString(R.string.no_list), ""));
                pCalendar.setVisibility(View.GONE);
                tvNew.setVisibility(View.GONE);
                fabRefresh.setVisibility(View.GONE);
                fabClear.setVisibility(View.VISIBLE);
                fabClose.setVisibility(View.VISIBLE);
                lvNoread.setVisibility(View.VISIBLE);
                ResizeAnim anim = new ResizeAnim(lvNoread, false,
                        (int) (getResources().getInteger(R.integer.height_list)
                                * getResources().getDisplayMetrics().density));
                anim.setDuration(800);
                lvNoread.clearAnimation();
                lvNoread.startAnimation(anim);
            }
        });
        lvNoread.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (!adNoread.getItem(i).getLink().equals("")) {
                    String link = adNoread.getItem(i).getLink();
                    adNoread.clear();
                    BrowserActivity.openActivity(getApplicationContext(), link, false);
//                    list.remove(i);
//                    adNoread.notifyDataSetChanged();
//                    int n = Integer.parseInt(tvNew.getText().toString());
//                    tvNew.setText(Integer.toString(n - 1));
                }
            }
        });
        fabClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                closeNoread();
            }
        });
        fabClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                closeNoread();
                tvNew.setText("0");
                lib.setCookies("", "", "");
            }
        });
        fabRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startLoad();
            }
        });
        status.setClick(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (status.onClick()) {
                    tvNew.setVisibility(View.VISIBLE);
                    fabRefresh.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void closeNoread() {
        ResizeAnim anim = new ResizeAnim(lvNoread, false, 10);
        anim.setDuration(600);
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                pCalendar.setVisibility(View.VISIBLE);
                tvNew.setVisibility(View.VISIBLE);
                fabRefresh.setVisibility(View.VISIBLE);
                fabClear.setVisibility(View.GONE);
                fabClose.setVisibility(View.GONE);
                lvNoread.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        lvNoread.clearAnimation();
        lvNoread.startAnimation(anim);
    }

    public void clearDays() {
        for (int i = 0; i < adCalendar.getItemCount(); i++) {
            adCalendar.getItem(i).clear();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        navigationView.setCheckedItem(R.id.nav_calendar);
        if (adNoread.getCount() == 0) {
            createNoreadList(true);
            if (adNoread.getCount() == 0 && lvNoread.getVisibility() == View.VISIBLE) {
                closeNoread();
            }
        }
    }

    private void initViews() {
        Date d = new Date();
        today_m = d.getMonth();
        today_y = d.getYear();
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(getResources().getString(R.string.calendar));
        tvNew = (TextView) findViewById(R.id.tvNew);
        status = new StatusBar(this, findViewById(R.id.pStatus));
        fabRefresh = findViewById(R.id.fabRefresh);
        fabClose = findViewById(R.id.fabClose);
        fabClear = findViewById(R.id.fabClear);
        lvNoread = (ListView) findViewById(R.id.lvNoread);
        adNoread = new ListAdapter(this);
        lvNoread.setAdapter(adNoread);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (lvNoread.getVisibility() == View.VISIBLE) {
            closeNoread();
        } else {
            super.onBackPressed();
        }
    }

    private void initCalendar() {
        pCalendar = findViewById(R.id.pCalendar);
        tvDate = (TextView) findViewById(R.id.tvDate);
        ivPrev = findViewById(R.id.ivPrev);
        ivNext = findViewById(R.id.ivNext);
        rvCalendar = (RecyclerView) findViewById(R.id.rvCalendar);
        GridLayoutManager layoutManager = new GridLayoutManager(this, 7);
        adCalendar = new CalendarAdapter();
        rvCalendar.setLayoutManager(layoutManager);
        rvCalendar.setAdapter(adCalendar);

        rvCalendar.addOnItemTouchListener(
                new RecyclerItemClickListener(this, new RecyclerItemClickListener.OnItemClickListener() {
                    @Override
                    public void onItemClick(View view, final int pos) {
                        int k = adCalendar.getItem(pos).getCount();
                        if (k == 1) {
                            adNoread.clear();
                            BrowserActivity.openActivity(getApplicationContext(),
                                    adCalendar.getItem(pos).getLink(0), false);
                        } else if (k > 1) {
                            PopupMenu popupMenu = new PopupMenu(CalendarActivity.this,
                                    rvCalendar.getChildAt(pos));
                            popupMenu.inflate(R.menu.menu_calendar);
                            List<ListItem> list = getList(adCalendar.getItem(pos).getLink(0));
                            String s;
                            for (int i = 0; i < 5; i++) {
                                if (i < k) {
                                    s = adCalendar.getItem(pos).getLink(i);
                                    for (int j = 0; j < list.size(); j++) {
                                        if (list.get(j).containsLink(s)) {
                                            popupMenu.getMenu().getItem(i)
                                                    .setTitle(list.get(j).getTitle());
                                            break;
                                        }
                                    }
                                } else {
                                    popupMenu.getMenu().getItem(i).setVisible(false);
                                }
                            }
                            popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                                @Override
                                public boolean onMenuItemClick(MenuItem item) {
                                    int index;
                                    if (item.getItemId() == R.id.link1)
                                        index = 0;
                                    else if (item.getItemId() == R.id.link2)
                                        index = 1;
                                    else if (item.getItemId() == R.id.link3)
                                        index = 2;
                                    else if (item.getItemId() == R.id.link4)
                                        index = 3;
                                    else
                                        index = 4;
                                    adNoread.clear();
                                    BrowserActivity.openActivity(getApplicationContext(),
                                            adCalendar.getItem(pos).getLink(index), false);
                                    return true;
                                }
                            });
                            popupMenu.show();
                        }
                    }
                })
        );
        rvCalendar.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        x = (int) event.getX(0);
                        y = (int) event.getY(0);
                        break;
                    case MotionEvent.ACTION_UP:
                        final int r = Math.abs(x -(int) event.getX(0));
//                        r2=Math.abs(y - (int) event.getY(0));
//                        Lib.LOG("r="+r);
//                        Lib.LOG("r2="+r2);
                        boolNotClick = false;
//                        if (r > r2) {
                            if (r < 175) { // next
                                if (ivNext.isEnabled())
                                    openMonth(1);
                                boolNotClick = true;
                            } else if (r > 350) { // prev
                                if (ivPrev.isEnabled())
                                    openMonth(-1);
                                boolNotClick = true;
                            }
//                        }
                        break;
                }
                return false;
            }
        });
        ivPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openMonth(-1);
            }
        });
        ivNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openMonth(1);
            }
        });
    }

    private void openMonth(int v) {
        if (task == null)
            createCalendar(v);
    }

    private List<ListItem> getList(String link) {
        List<ListItem> data = new ArrayList<ListItem>();
        int n = link.indexOf(".") + 1;
        String t, f = Lib.LIST + link.substring(n, n + 5);
        File file = new File(getFilesDir() + f);
        while (true) {
            if (file.exists()) {
                try {
                    BufferedReader br = new BufferedReader(new FileReader(file));
                    while ((t = br.readLine()) != null) {
                        if (f.contains("p")) {
                            data.add(new ListItem(t, br.readLine()));
                        } else {
                            if (t.contains("("))
                                t = t.substring(0, t.indexOf("(") - 1);
                            data.add(new ListItem(getResources().getString(R.string.katren)
                                    + " " + t, br.readLine()));
                        }
                    }
                    br.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (f.contains("p"))
                break;
            f += "p";
            file = new File(getFilesDir() + f);
        }
        return data;
    }

    private void createCalendar(int v) {
        Date d = (Date) dCurrent.clone();
        if (v != 0)
            d.setMonth(d.getMonth() + v);
        tvDate.setText(getResources().getStringArray(R.array.months)[d.getMonth()]
                + "\n" + (d.getYear() + 1900));
        adCalendar.clear();
        for (int i = -1; i > -7; i--)
            adCalendar.addItem(new CalendarItem(this, i, R.color.light_gray));
        adCalendar.addItem(new CalendarItem(this, 0, R.color.light_gray));
        int n;
        final int m = d.getMonth();
        d.setDate(1);
        dCurrent = (Date) d.clone();
        if (d.getDay() != 1) {
            if (d.getDay() == 0) //sunday
                d.setDate(-5);
            else
                d.setDate(2 - d.getDay());
            n = d.getDate();
            while (d.getDate() > 1) {
                adCalendar.addItem(new CalendarItem(this, d.getDate(), R.color.gray));
                n++;
                d.setDate(n);
            }
        }
        n = 1;
        while (d.getMonth() == m) {
            adCalendar.addItem(new CalendarItem(this, d.getDate(), R.color.white));
            n++;
            d.setDate(n);
        }
        n = 1;
        while (d.getDay() != 1) {
            adCalendar.addItem(new CalendarItem(this, d.getDate(), R.color.gray));
            n++;
            d.setDate(n);
        }
        openCalendar(true);
        adCalendar.notifyDataSetChanged();
        if (dCurrent.getYear() == 116)
            ivPrev.setEnabled(dCurrent.getMonth() != 0);
        if (dCurrent.getYear() == today_y)
            ivNext.setEnabled(dCurrent.getMonth() != today_m);
        else
            ivNext.setEnabled(true);
    }

    public boolean isCurMonth() {
        return dCurrent.getMonth() == today_m && dCurrent.getYear() == today_y;
    }

    private void openCalendar(boolean boolLoad) {
        try {
            if (task != null)
                return;
            File file = new File(getFilesDir() + CALENDAR +
                    dCurrent.getMonth() + "." + dCurrent.getYear());
            if (!file.exists()) {
                if (boolLoad)
                    startLoad();
            } else {
                if (isCurMonth() && boolLoad) {
                    long t = lib.getTimeLastVisit();
                    t = System.currentTimeMillis() - t;
                    if (t > 3600000) {
                        startLoad();
                        return;
                    }
                }
                int i;
                String s;
                BufferedReader br = new BufferedReader(new FileReader(file));
                while ((s = br.readLine()) != null) {
                    i = adCalendar.indexOf(Integer.parseInt(s));
                    s = br.readLine();
                    while (s != null && !s.equals("")) {
                        if (i > -1)
                            adCalendar.getItem(i).addLink(s);
                        s = br.readLine();
                    }
                }
                br.close();
            }
            adCalendar.notifyDataSetChanged();
        } catch (Exception e) {
            e.printStackTrace();
            if (boolLoad)
                startLoad();
        }
    }

    private void startLoad() {
        setStatus(true);
        task = new CalendarTask(this);
        if (isCurMonth()) adNoread.clear();
        int n = (isCurMonth() ? 1 : 0);
        task.execute(dCurrent.getYear(), dCurrent.getMonth(), n);
    }

    public void finishLoad(boolean suc) {
        task = null;
        if (suc) {
            setStatus(false);
        } else {
            status.setCrash(true);
        }
        if (adNoread.getCount() == 0)
            createNoreadList(false);
        clearDays();
        openCalendar(false);
    }

    private void createNoreadList(boolean boolLoad) {
        try {
            File file = new File(getFilesDir() + File.separator + Lib.NOREAD);
            if (file.exists()) {
                String s;
                BufferedReader br = new BufferedReader(new InputStreamReader(openFileInput(file.getName())));
                while ((s = br.readLine()) != null) {
                    adNoread.addItem(new ListItem(s, br.readLine()));
                }
                br.close();
            }
            setNew(adNoread.getCount(), !boolLoad);
            adNoread.notifyDataSetChanged();
        } catch (Exception e) {
            e.printStackTrace();
            if (boolLoad && isCurMonth())
                startLoad();
        }
    }

    private void setNew(int n, boolean boolAnim) {
        String s = tvNew.getText().toString();
        tvNew.setText(Integer.toString(n));
        if (boolAnim) {
            boolean b = s.contains(".");
            if (!b)
                b = (n > Integer.parseInt(s) && n > 0) || n == 20;
            if (b) {
                ResizeAnim anim = new ResizeAnim(tvNew, true,
                        (int) (56 * getResources().getDisplayMetrics().density));
                anim.setDuration(700);
                tvNew.clearAnimation();
                tvNew.startAnimation(anim);
            }
        }
    }

    private void setStatus(boolean boolStart) {
        status.setCrash(false);
        status.setLoad(boolStart);
        if (boolStart) {
            tvNew.setVisibility(View.GONE);
            fabRefresh.setVisibility(View.GONE);
        } else {
            tvNew.setVisibility(View.VISIBLE);
            fabRefresh.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        status.setCrash(false);
        downloadAll();
        return super.onOptionsItemSelected(item);
    }
}
