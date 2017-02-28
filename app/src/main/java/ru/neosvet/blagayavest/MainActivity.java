package ru.neosvet.blagayavest;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.PopupMenu;
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

import ru.neosvet.ui.ListAdapter;
import ru.neosvet.ui.ListItem;
import ru.neosvet.ui.MyActivity;
import ru.neosvet.ui.StatusBar;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.MainTask;

public class MainActivity extends MyActivity {
    public static final String MAIN = "/main", NEWS = "/news", MEDIA = "/media", CURRENT_TAB = "tab", END = "<end>";
    private ListAdapter adMain;
    private NavigationView navigationView;
    private View fabRefresh;
    private StatusBar status;
    private MainTask task = null;
    private TabHost tabHost;
    private ListView lvMain;
    private int x, y;
    private boolean boolNotClick = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
        setViews();
        initTabs();
        restoreActivityState(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(CURRENT_TAB, tabHost.getCurrentTab());
        outState.putSerializable(Lib.TASK, task);
        super.onSaveInstanceState(outState);
    }

    protected void restoreActivityState(Bundle state) {
        super.restoreActivityState(state);
        if (state == null) {
            tabHost.setCurrentTab(0);
        } else {
            if (state.getInt(CURRENT_TAB) == 1)
                tabHost.setCurrentTab(0);
            tabHost.setCurrentTab(state.getInt(CURRENT_TAB));
            task = (MainTask) state.getSerializable(Lib.TASK);
            if (task != null) {
                fabRefresh.setVisibility(View.GONE);
                status.setLoad(true);
                task.setAct(this);
            }
        }
    }

    private void initTabs() {
        tabHost = (TabHost) findViewById(R.id.thMain);
        tabHost.setup();
        TabHost.TabSpec tabSpec;

        tabSpec = tabHost.newTabSpec(MAIN);
        tabSpec.setIndicator(getResources().getString(R.string.main),
                getResources().getDrawable(R.drawable.none));
        tabSpec.setContent(R.id.lvMain);
        tabHost.addTab(tabSpec);

        tabSpec = tabHost.newTabSpec(NEWS);
        tabSpec.setIndicator(getResources().getString(R.string.news),
                getResources().getDrawable(R.drawable.none));
        tabSpec.setContent(R.id.lvMain);
        tabHost.addTab(tabSpec);

        tabSpec = tabHost.newTabSpec(MEDIA);
        tabSpec.setIndicator(getResources().getString(R.string.media),
                getResources().getDrawable(R.drawable.none));
        tabSpec.setContent(R.id.lvMain);
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
                if (name.equals(MAIN))
                    MainActivity.this.setTitle(getResources().getString(R.string.main));
                else if (name.equals(NEWS))
                    MainActivity.this.setTitle(getResources().getString(R.string.news));
                else if (name.equals(MEDIA))
                    MainActivity.this.setTitle(getResources().getString(R.string.media));
                if (getFile(name).exists())
                    openList(name, true);
                else
                    startLoad(name);
            }
        });
    }

    private void initViews() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        fabRefresh = findViewById(R.id.fabRefresh);
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
        navigationView.setCheckedItem(R.id.nav_main);
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
                startLoad(tabHost.getCurrentTabTag());
            }
        });
        lvMain = (ListView) findViewById(R.id.lvMain);
        adMain = new ListAdapter(this);
        lvMain.setAdapter(adMain);
        lvMain.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, final int pos, long l) {
                if (boolNotClick) return;
                if (adMain.getItem(pos).getCount() == 1) {
                    String link = adMain.getItem(pos).getLink();
                    if (link.equals("#") || link.equals("@")) return;
                    if (tabHost.getCurrentTab() == 0) { // main
                        if (pos == 1 || pos == 5) { //poslaniya
                            Intent intent = new Intent(MainActivity.this, BookActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                            startActivity(intent);
                        } else if (pos == adMain.getCount() - 2) { //rss
//                            Intent intent = new Intent(MainActivity.this, Summary Activity.class);
//                            intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
//                            startActivity(intent);
                        } else if (pos == 6 || pos == 7) { //no article
                            BrowserActivity.openActivity(getApplicationContext(), link, false);
                        } else
                            OpenPage(link);
                    } else {
                        OpenPage(link);
                    }
                } else {
                    PopupMenu pMenu = new PopupMenu(MainActivity.this, view);
                    pMenu.inflate(R.menu.menu_calendar);
                    for (int i = 0; i < 5; i++) {
                        if (i < adMain.getItem(pos).getCount())
                            pMenu.getMenu().getItem(i).setTitle(adMain.getItem(pos).getHead(i));
                        else
                            pMenu.getMenu().getItem(i).setVisible(false);
                    }
                    pMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
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
                            OpenPage(adMain.getItem(pos).getLink(index));
                            return true;
                        }
                    });
                    pMenu.show();
                }
            }
        });
        lvMain.setOnTouchListener(new View.OnTouchListener() {
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
                                int t = tabHost.getCurrentTab();
                                if (x > x2) { // next
                                    if (t < 3)
                                        tabHost.setCurrentTab(t + 1);
                                    boolNotClick = true;
                                } else if (x < x2) { // prev
                                    if (t > 0)
                                        tabHost.setCurrentTab(t - 1);
                                    boolNotClick = true;
                                }
                            }
                        break;
                }
                return false;
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

    private void openList(String name, boolean boolLoad) {
        try {
            adMain.clear();
            BufferedReader br = new BufferedReader(new FileReader(getFile(name)));
            String t, d, l, h;
            int i = 0;
            while ((t = br.readLine()) != null) {
                d = br.readLine();
                l = br.readLine();
                if (!l.equals(END))
                    h = br.readLine();
                else
                    h = END;
                if (l.equals("#")) {
                    adMain.addItem(new ListItem(t, true));
                } else {
                    adMain.addItem(new ListItem(t), d);
                    if (!h.equals(END)) {
                        if (l.equals(END)) l = "";
                        adMain.getItem(i).addLink(h, l);
                        l = br.readLine();
                        while (!l.equals(END)) {
                            h = br.readLine();
                            adMain.getItem(i).addLink(h, l);
                            l = br.readLine();
                        }
                    } else
                        adMain.getItem(i).addLink("", l);
                }
                i++;
            }
            br.close();
            adMain.notifyDataSetChanged();
            lvMain.smoothScrollToPosition(0);
        } catch (Exception e) {
            e.printStackTrace();
            if (boolLoad)
                startLoad(tabHost.getCurrentTabTag());
        }
    }

    private void OpenPage(String url) {
        if (url.contains("http") || url.contains("mailto")) {
            lib.openInApps(url);
        } else {
            BrowserActivity.openActivity(getApplicationContext(), url, !url.contains("press"));
        }
    }

    private void startLoad(String name) {
        status.setCrash(false);
        String url = Lib.SITE;
        switch (name) {
            case NEWS:
                url += "novosti.html";
                break;
            case MEDIA:
                url += "media.html";
                break;
        }
        fabRefresh.setVisibility(View.GONE);
        task = new MainTask(this);
        task.execute(url, getFile(name).toString());
        status.setLoad(true);
    }

    private File getFile(String name) {
        return new File(getFilesDir() + name);
    }

    public void finishLoad(String name) {
        task = null;
        if (name == null) {
            status.setCrash(true);
        } else {
            fabRefresh.setVisibility(View.VISIBLE);
            status.setLoad(false);
            openList(name, false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        status.setCrash(false);
        downloadAll();
        return super.onOptionsItemSelected(item);
    }
}
