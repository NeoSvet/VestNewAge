package ru.neosvet.blagayavest;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import ru.neosvet.ui.ListAdapter;
import ru.neosvet.ui.ListItem;
import ru.neosvet.ui.MyActivity;
import ru.neosvet.ui.StatusBar;
import ru.neosvet.utils.BookTask;
import ru.neosvet.utils.Lib;

public class BookActivity extends MyActivity {
    public final String POS = "pos", KAT = "kat", CURRENT_TAB = "tab";
    private final DateFormat df = new SimpleDateFormat("MM.yy");
    private ListAdapter adBook;
    private NavigationView navigationView;
    private View fabRefresh, ivPrev, ivNext;
    private TextView tvDate;
    private StatusBar status;
    private BookTask task;
    private TabHost tabHost;
    private ListView lvBook;
    private int x, y, tab;
    private boolean boolNotClick = false;
    private Date dKat, dPos;
    private SharedPreferences pref;
    private SharedPreferences.Editor editor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book);
        initViews();
        setViews();
        initTabs();
        restoreActivityState(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(CURRENT_TAB, tab);
        outState.putSerializable(Lib.TASK, task);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onStop() {
        super.onStop();
        editor.putLong(KAT, dKat.getTime());
        editor.putLong(POS, dPos.getTime());
        editor.apply();
    }

    protected void restoreActivityState(Bundle state) {
        super.restoreActivityState(state);
        long t = System.currentTimeMillis();
        dKat = new Date(pref.getLong(KAT, t));
        dPos = new Date(pref.getLong(POS, t));
        if (state == null) {
            tabHost.setCurrentTab(0);
            tab = 0;
        } else {
            tab = state.getInt(CURRENT_TAB);
            if (tab == 1)
                tabHost.setCurrentTab(0);
            tabHost.setCurrentTab(tab);
            task = (BookTask) state.getSerializable(Lib.TASK);
            if (task != null) {
                fabRefresh.setVisibility(View.GONE);
                task.setAct(this);
                status.setLoad(true);
            }
        }
    }

    private void initTabs() {
        tabHost = (TabHost) findViewById(R.id.thBook);
        tabHost.setup();
        TabHost.TabSpec tabSpec;

        tabSpec = tabHost.newTabSpec(KAT);
        tabSpec.setIndicator(getResources().getString(R.string.katreny),
                getResources().getDrawable(R.drawable.none));
        tabSpec.setContent(R.id.pBook);
        tabHost.addTab(tabSpec);

        tabSpec = tabHost.newTabSpec(POS);
        tabSpec.setIndicator(getResources().getString(R.string.poslaniya),
                getResources().getDrawable(R.drawable.none));
        tabSpec.setContent(R.id.pBook);
        tabHost.addTab(tabSpec);

        TabWidget widget = tabHost.getTabWidget();
        for (int i = 0; i < widget.getChildCount(); i++) {
            View v = widget.getChildAt(i);
            TextView tv = (TextView) v.findViewById(android.R.id.title);
            if (tv != null) {
                tv.setMaxLines(1);
                v.setBackgroundResource(R.drawable.table_selector);
            }
        }
        tabHost.setCurrentTab(1);
        tabHost.setOnTabChangedListener(new TabHost.OnTabChangeListener() {
            public void onTabChanged(String name) {
                if (name.equals(KAT))
                    BookActivity.this.setTitle(getResources().getString(R.string.katreny));
                else
                    BookActivity.this.setTitle(getResources().getString(R.string.poslaniya));
                tab = tabHost.getCurrentTab();
                if (getFile().exists())
                    openList(true);
                else
                    startLoad(tab);
            }
        });
    }

    private void openList(boolean boolLoad) {
        try {
            adBook.clear();
            Date d;
            boolean bP = false;
            if (tab == 0)
                d = dKat;
            else {
                d = dPos;
                bP = true;
            }
            File f = getFile(d, bP);
            int m = d.getMonth(), y = d.getYear();
            while (!f.exists()) {
                if (m == 0) {
                    if (y == 116) {
                        if (boolLoad)
                            startLoad(tab);
                        return;
                    } else
                        d.setYear(--y);
                    m = 12;
                }
                d.setMonth(--m);
                f = getFile(d, bP);
            }
            tvDate.setText(getResources().getStringArray(R.array.months)[d.getMonth()]
                    + "\n" + (d.getYear() + 1900));
            ivPrev.setEnabled(getFile(setDate(d,-1),bP).exists());
            ivNext.setEnabled(getFile(setDate(d,1),bP).exists());
            //f.lastModified()
            BufferedReader br = new BufferedReader(new FileReader(f));
            String t, s;
            while ((t = br.readLine()) != null) {
                if (bP) {
                    s = br.readLine();
                    t += " (" + getResources().getString(R.string.from) + " " + s.substring(11, s.lastIndexOf(".")) + ")";
                    adBook.addItem(new ListItem(t, s));
                } else
                    adBook.addItem(new ListItem(t, br.readLine()));
            }
            br.close();
            adBook.notifyDataSetChanged();
            lvBook.smoothScrollToPosition(0);
        } catch (Exception e) {
            e.printStackTrace();
            if (boolLoad)
                startLoad(tab);
        }
    }

    private File getFile() {
        String f = Lib.LIST;
        if (tab == 0)
            f += df.format(dKat);
        else
            f += df.format(dPos) + "p";
        return new File(getFilesDir() + f);
    }

    private File getFile(Date d, boolean addP) {
        String f = Lib.LIST + df.format(d) + (addP ? "p" : "");
        return new File(getFilesDir() + f);
    }

    private void initViews() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        pref = getSharedPreferences("book", MODE_PRIVATE);
        editor = pref.edit();
        fabRefresh = findViewById(R.id.fabRefresh);
        tvDate = (TextView) findViewById(R.id.tvDate);
        ivPrev = findViewById(R.id.ivPrev);
        ivNext = findViewById(R.id.ivNext);
        status = new StatusBar(this, findViewById(R.id.pStatus));
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        navigationView.setCheckedItem(R.id.nav_book);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    private void setViews() {
        fabRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startLoad(tab);
            }
        });
        lvBook = (ListView) findViewById(R.id.lvBook);
        adBook = new ListAdapter(this);
        lvBook.setAdapter(adBook);
        lvBook.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, final int pos, long l) {
                if (boolNotClick) return;
                BrowserActivity.openActivity(getApplicationContext(), adBook.getItem(pos).getLink(), false);
//
//                    PopupMenu pMenu = new PopupMenu(BookActivity.this, view);
//                    pMenu.inflate(R.menu.menu_calendar);
//                    pMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
//                        @Override
//                        public boolean onMenuItemClick(MenuItem item) {
//                            if (item.getItemId() == R.id.link1)
//                            return true;
//                        }
//                    });
//                    pMenu.show();
//                }
            }
        });
        lvBook.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        x = (int) event.getX(0);
                        y = (int) event.getY(0);
                        break;
                    case MotionEvent.ACTION_UP:
                        final int x2 = (int) event.getX(0), r = Math.abs(x - x2);
                        boolNotClick = false;
                        if (r > (int) (30 * getResources().getDisplayMetrics().density))
                            if (r > Math.abs(y - (int) event.getY(0))) {
                                if (x > x2) { // next
                                    if(ivNext.isEnabled())
                                        openMonth(1);
                                    boolNotClick = true;
                                } else if (x < x2) { // prev
                                    if(ivPrev.isEnabled())
                                        openMonth(-1);
                                    boolNotClick = true;
                                }
                            }
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
        status.setClick(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (status.onClick())
                    fabRefresh.setVisibility(View.VISIBLE);
            }
        });
    }

    private void openMonth(int v) {
        if (task == null) {
            setDate(v);
            openList(true);
        }
    }

    private void setDate(int i) {
        if (tab == 0)
            dKat = setDate(dKat, i);
        else
            dPos = setDate(dPos, i);
    }

    private Date setDate(Date d, int i) {
        int m, y;
        m = d.getMonth() + i;
        y = d.getYear();
        if (m == -1) {
            y--;
            m = 11;
        } else if (m == 12) {
            y++;
            m = 0;
        }
        return new Date(y, m, 1);
    }

    private void startLoad(int tab) {
        status.setCrash(false);
        fabRefresh.setVisibility(View.GONE);
        task = new BookTask(this);
        task.execute((byte) tab);
        status.setLoad(true);
    }

    public void finishLoad(boolean suc) {
        task = null;
        if (suc) {
            fabRefresh.setVisibility(View.VISIBLE);
            status.setLoad(false);
            openList(false);
        } else {
            status.setCrash(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        status.setCrash(false);
        downloadAll();
        return super.onOptionsItemSelected(item);
    }
}
