package ru.neosvet.vestnewage.fragment;

import android.app.Fragment;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.widget.PopupMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
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
import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.BrowserActivity;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.task.SiteTask;

public class SiteFragment extends Fragment {
    public static final String MAIN = "/main", NEWS = "/news", MEDIA = "/media", CURRENT_TAB = "tab", END = "<end>";
    private MainActivity act;
    private ListAdapter adMain;
    private View container, fabRefresh, tvEmptySite;
    private Animation anMin, anMax;
    private SiteTask task = null;
    private TabHost tabHost;
    private ListView lvMain;
    private int x, y, tab = 0;
    private boolean boolNotClick = false, boolScrollToFirst = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.site_fragment, container, false);
        act = (MainActivity) getActivity();
        initViews();
        setViews();
        initTabs();
        restoreActivityState(savedInstanceState);
        return this.container;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(CURRENT_TAB, tabHost.getCurrentTab());
        outState.putSerializable(Const.TASK, task);
        super.onSaveInstanceState(outState);
    }

    private void restoreActivityState(Bundle state) {
        if (state != null) {
            tab = state.getInt(CURRENT_TAB);
            task = (SiteTask) state.getSerializable(Const.TASK);
            if (task != null) {
                if (task.getStatus() == AsyncTask.Status.RUNNING) {
                    fabRefresh.setVisibility(View.GONE);
                    act.status.setLoad(true);
                    task.setFrm(this);
                } else task = null;
            }
        }
        if (tab == 1) {
            tabHost.setCurrentTab(0);
            tabHost.setCurrentTab(1);
        } else
            tabHost.setCurrentTab(tab);
    }

    private void initTabs() {
        tabHost = (TabHost) container.findViewById(R.id.thMain);
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
                    act.setTitle(getResources().getString(R.string.main));
                else if (name.equals(NEWS))
                    act.setTitle(getResources().getString(R.string.news));
                else if (name.equals(MEDIA))
                    act.setTitle(getResources().getString(R.string.media));
                File f = getFile(name);
                if (f.exists())
                    openList(f, true);
                else
                    startLoad(name);
            }
        });
    }

    private void initViews() {
        tvEmptySite = container.findViewById(R.id.tvEmptySite);
        fabRefresh = container.findViewById(R.id.fabRefresh);
        anMin = AnimationUtils.loadAnimation(act, R.anim.minimize);
        anMin.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                fabRefresh.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        anMax = AnimationUtils.loadAnimation(act, R.anim.maximize);
    }

    private void setViews() {
        fabRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startLoad(tabHost.getCurrentTabTag());
            }
        });
        lvMain = (ListView) container.findViewById(R.id.lvMain);
        adMain = new ListAdapter(act);
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
                            act.setFragment(R.id.nav_book);
                        } else if (pos == adMain.getCount() - 2) { //rss
//                            Intent intent = new Intent(SiteFragment.this, Summary Activity.class);
//                            intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
//                            startActivity(intent);
                        } else if (pos == 6 || pos == 7) { //no article
                            BrowserActivity.openReader(act, link, null);
                        } else
                            openPage(link);
                    } else {
                        openPage(link);
                    }
                } else {
                    PopupMenu pMenu = new PopupMenu(act, view);
                    pMenu.inflate(R.menu.menu_links);
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
                            openPage(adMain.getItem(pos).getLink(index));
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
                        if (!act.status.startMin())
                            fabRefresh.startAnimation(anMin);
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
                    case MotionEvent.ACTION_CANCEL:
                        if (!act.status.startMax()) {
                            fabRefresh.setVisibility(View.VISIBLE);
                            fabRefresh.startAnimation(anMax);
                        }
                        break;
                }
                return false;
            }
        });
        act.status.setClick(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (act.status.onClick())
                    fabRefresh.setVisibility(View.VISIBLE);
                else if (act.status.isTime())
                    startLoad(tabHost.getCurrentTabTag());
            }
        });
        lvMain.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int scrollState) {
                if (scrollState == SCROLL_STATE_IDLE && boolScrollToFirst) {
                    if (lvMain.getFirstVisiblePosition() > 0)
                        lvMain.smoothScrollToPosition(0);
                    else
                        boolScrollToFirst = false;
                }
            }

            @Override
            public void onScroll(AbsListView absListView, int firstVisibleItem,
                                 int visibleItemCount, int totalItemCount) {
            }
        });
    }

    private void openList(File f, boolean boolLoad) {
        try {
            adMain.clear();
            if (act.status.checkTime(f.lastModified()))
                fabRefresh.setVisibility(View.GONE);
            else
                fabRefresh.setVisibility(View.VISIBLE);
            BufferedReader br = new BufferedReader(new FileReader(f));
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
            if (lvMain.getFirstVisiblePosition() > 0) {
                boolScrollToFirst = true;
                lvMain.smoothScrollToPosition(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (boolLoad)
                startLoad(tabHost.getCurrentTabTag());
        }

        if (adMain.getCount() == 0)
            tvEmptySite.setVisibility(View.VISIBLE);
        else
            tvEmptySite.setVisibility(View.GONE);
    }

    private void openPage(String url) {
        if (url.contains("http") || url.contains("mailto")) {
            if (url.contains(Const.SITE)) {
                act.lib.openInApps(url, getResources().getString(R.string.to_load));
            } else {
                act.lib.openInApps(url, null);
            }
        } else {
            BrowserActivity.openReader(act, url, null);
        }
    }

    private void startLoad(String name) {
        act.status.setCrash(false);
        String url = Const.SITE;
        switch (name) {
            case NEWS:
                url += "novosti.html";
                break;
            case MEDIA:
                url += "media.html";
                break;
        }
        fabRefresh.setVisibility(View.GONE);
        task = new SiteTask(this);
        task.execute(url, getFile(name).toString());
        act.status.setLoad(true);
    }

    private File getFile(String name) {
        return new File(act.getFilesDir() + name);
    }

    public void finishLoad(String name) {
        task = null;
        if (name == null) {
            act.status.setCrash(true);
        } else {
            fabRefresh.setVisibility(View.VISIBLE);
            act.status.setLoad(false);
            openList(getFile(name), false);
        }
    }

    public void setTab(int tab) {
        this.tab = tab;
    }
}