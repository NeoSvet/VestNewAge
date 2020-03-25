package ru.neosvet.vestnewage.fragment;

import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.PopupMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import androidx.work.Data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import ru.neosvet.utils.BackFragment;
import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.BrowserActivity;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.list.ListAdapter;
import ru.neosvet.vestnewage.list.ListItem;
import ru.neosvet.vestnewage.model.SiteModel;

public class SiteFragment extends BackFragment implements Observer<Data> {
    public static final String MAIN = "/main", NEWS = "/news", FORUM = "intforum.html", NOVOSTI = "novosti.html", END = "<end>";
    private MainActivity act;
    private ListAdapter adMain;
    private View container, fabRefresh, tvEmptySite;
    private SiteModel model;
    private TabHost tabHost;
    private ListView lvMain;
    private int x, y, tab = 0;
    private boolean notClick = false, scrollToFirst = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.site_fragment, container, false);
        act = (MainActivity) getActivity();
        initViews();
        setViews();
        model = ViewModelProviders.of(act).get(SiteModel.class);
        initTabs();
        restoreState(savedInstanceState);
        return this.container;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (ProgressHelper.isBusy())
            ProgressHelper.removeObservers(act);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (ProgressHelper.isBusy())
            initLoad();
    }

    private void initLoad() {
        ProgressHelper.addObserver(act, this);
        fabRefresh.setVisibility(View.GONE);
        act.status.setLoad(true);
    }

    @Override
    public void onChanged(@Nullable Data data) {
        if (!ProgressHelper.isBusy())
            return;
        if (data.getBoolean(Const.START, false)) {
            act.status.loadText();
            return;
        }
        if (data.getBoolean(Const.DIALOG, false)) {
            act.status.setText(data.getString(Const.MSG));
            return;
        }
        if (data.getBoolean(Const.LIST, false)) {
            openList(getFile(data.getString(Const.FILE)), false);
            return;
        }
        if (data.getBoolean(Const.FINISH, false)) {
            String error = data.getString(Const.ERROR);
            ProgressHelper.removeObservers(act);
            if (error != null) {
                act.status.setError(error);
                return;
            }
            fabRefresh.setVisibility(View.VISIBLE);
            act.status.setLoad(false);
            ProgressHelper.setBusy(false);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(Const.TAB, tabHost.getCurrentTab());
        super.onSaveInstanceState(outState);
    }

    private void restoreState(Bundle state) {
        if (state != null) {
            act.setCurFragment(this);
            tab = state.getInt(Const.TAB);
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

        tabSpec = tabHost.newTabSpec(NEWS);
        tabSpec.setIndicator(getResources().getString(R.string.news),
                getResources().getDrawable(R.drawable.none));
        tabSpec.setContent(R.id.lvMain);
        tabHost.addTab(tabSpec);

        tabSpec = tabHost.newTabSpec(MAIN);
        tabSpec.setIndicator(getResources().getString(R.string.site),
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
                    act.setTitle(getResources().getString(R.string.site));
                else
                    act.setTitle(getResources().getString(R.string.news));
                File f = getFile(name);
                if (f.exists())
                    openList(f, true);
                else
                    startLoad(name);
            }
        });
        tabHost.setCurrentTab(0);
    }

    private void initViews() {
        tvEmptySite = container.findViewById(R.id.tvEmptySite);
        fabRefresh = container.findViewById(R.id.fabRefresh);
        act.fab = fabRefresh;
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
                if (notClick) return;
                if (act.checkBusy()) return;
                if (adMain.getItem(pos).getCount() == 1) {
                    String link = adMain.getItem(pos).getLink();
                    if (link.equals("#") || link.equals("@")) return;
                    if (tabHost.getCurrentTab() == 1) { // site
                        if (link.contains(Const.RSS)) {
                            act.setFragment(R.id.nav_rss, true);
                        } else if (link.contains("/poems")) {
                            act.openBook(link, true);
                        } else if (pos == 1) { //tolkovaniya
                            act.setFragment(R.id.nav_book, true);
                        } else if (pos == 5) { //poslaniya
                            act.openBook(link, false);
                        } else if (pos == 6 || pos == 7) { //no article
                            BrowserActivity.openReader(act, link, null);
                        } else
                            openPage(link);
                    } else {
                        openPage(link);
                    }
                } else {
                    PopupMenu pMenu = new PopupMenu(act, view);
                    for (int i = 0; i < adMain.getItem(pos).getCount(); i++) {
                        pMenu.getMenu().add(adMain.getItem(pos).getHead(i));
                    }
                    pMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            String title = item.getTitle().toString();
                            for (int i = 0; i < adMain.getItem(pos).getCount(); i++) {
                                if (adMain.getItem(pos).getHead(i).equals(title)) {
                                    openPage(adMain.getItem(pos).getLink(i));
                                    break;
                                }
                            }
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
                            act.startAnimMin();
                        x = (int) event.getX(0);
                        y = (int) event.getY(0);
                        break;
                    case MotionEvent.ACTION_UP:
                        final int x2 = (int) event.getX(0), r = Math.abs(x - x2);
                        notClick = false;
                        if (r > (int) (30 * getResources().getDisplayMetrics().density))
                            if (r > Math.abs(y - (int) event.getY(0))) {
                                int t = tabHost.getCurrentTab();
                                if (x > x2) { // next
                                    if (t < 2)
                                        tabHost.setCurrentTab(t + 1);
                                    notClick = true;
                                } else if (x < x2) { // prev
                                    if (t > 0)
                                        tabHost.setCurrentTab(t - 1);
                                    notClick = true;
                                }
                            }
                    case MotionEvent.ACTION_CANCEL:
                        if (!act.status.startMax())
                            act.startAnimMax();
                        break;
                }
                return false;
            }
        });
        act.status.setClick(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!act.status.isStop()) {
                    act.status.setLoad(false);
                    ProgressHelper.cancelled();
                    ProgressHelper.setBusy(false);
                    return;
                }
                if (act.status.onClick())
                    fabRefresh.setVisibility(View.VISIBLE);
                else if (act.status.isTime())
                    startLoad(tabHost.getCurrentTabTag());
            }
        });
        lvMain.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int scrollState) {
                if (scrollState == SCROLL_STATE_IDLE && scrollToFirst) {
                    if (lvMain.getFirstVisiblePosition() > 0)
                        lvMain.smoothScrollToPosition(0);
                    else
                        scrollToFirst = false;
                }
            }

            @Override
            public void onScroll(AbsListView absListView, int firstVisibleItem,
                                 int visibleItemCount, int totalItemCount) {
            }
        });
    }

    private void openList(File f, boolean loadIfNeed) {
        try {
            adMain.clear();
            if (act.status.checkTime(f.lastModified() / DateHelper.SEC_IN_MILLS))
                fabRefresh.setVisibility(View.GONE);
            else
                fabRefresh.setVisibility(View.VISIBLE);
            if(tabHost.getCurrentTab() == 1) { //main
                adMain.addItem(new ListItem(getResources().getString(R.string.novosti)), "");
                adMain.getItem(0).addLink(FORUM);
            }
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
                scrollToFirst = true;
                lvMain.smoothScrollToPosition(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (loadIfNeed)
                startLoad(tabHost.getCurrentTabTag());
        }
        adMain.notifyDataSetChanged();
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
        if (ProgressHelper.isBusy())
            return;
        String url = Const.SITE;
        if (name.equals(NEWS))
            url += NOVOSTI;
        initLoad();
        act.status.startText();
        model.startLoad(url, getFile(name).toString());
    }

    private File getFile(String name) {
        return new File(act.getFilesDir() + name);
    }

    public void setTab(int tab) {
        this.tab = tab;
    }
}
