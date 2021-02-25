package ru.neosvet.vestnewage.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
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
import ru.neosvet.vestnewage.helpers.DevadsHelper;
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
    private DevadsHelper ads;
    private int x, y, tab = 0;
    private boolean notClick = false, scrollToFirst = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = inflater.inflate(R.layout.site_fragment, container, false);
        act = (MainActivity) getActivity();
        ads = new DevadsHelper(act);
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
    public void onStop() {
        super.onStop();
        ads.close();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (ProgressHelper.isBusy())
            initLoad();
        ads.reopen();
    }

    private void initLoad() {
        fabRefresh.setVisibility(View.GONE);
        act.status.setLoad(true);
        ProgressHelper.addObserver(act, this);
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
            act.status.setProgress(data.getInt(Const.PROG, 0));
            return;
        }
        if (data.getBoolean(Const.LIST, false)) {
            String f = data.getString(Const.FILE);
            if (tabHost.getCurrentTabTag().equals(f))
                openList(getFile(f), false);
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
        outState.putInt(Const.TAB, tab);
        outState.putInt(Const.ADS, ads.getIndex());
        super.onSaveInstanceState(outState);
    }

    private void restoreState(Bundle state) {
        int index_ads = -1;
        if (state != null) {
            act.setCurFragment(this);
            tab = state.getInt(Const.TAB);
            index_ads = state.getInt(Const.ADS);
        } else
            tab = getArguments().getInt(Const.TAB);
        switch (tab) {
            case 0:
                tabHost.setCurrentTab(0);
                break;
            case 1:
                tabHost.setCurrentTab(0);
                tabHost.setCurrentTab(1);
                break;
            case 2:
                tabHost.setCurrentTab(0);
                tab = 2;
                showDevads();
                if (index_ads > -1) {
                    ads.setIndex(index_ads);
                    ads.showAd(adMain.getItem(ads.getIndex()).getTitle(),
                            adMain.getItem(ads.getIndex()).getLink(),
                            adMain.getItem(ads.getIndex()).getHead(0));
                }
                break;
        }
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
        lvMain = container.findViewById(R.id.lvMain);
        adMain = new ListAdapter(act);
        lvMain.setAdapter(adMain);
        lvMain.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, final int pos, long l) {
                if (notClick) return;
                if (act.checkBusy()) return;
                if (isAds(pos))
                    return;
                if (adMain.getItem(pos).getCount() == 1) {

                    String link = adMain.getItem(pos).getLink();
                    if (link.equals("#") || link.equals("@")) return;
                    if (tabHost.getCurrentTab() == 1) { // site
                        if (link.contains("rss")) {
                            act.setFragment(R.id.nav_rss, true);
                        } else if (link.contains("poems")) {
                            act.openBook(link, true);
                        } else if (link.contains("tolkovaniya") || link.contains("2016")) {
                            act.openBook(link, false);
                        } else if (link.contains("files") && !link.contains("http")) {
                            openPage(Const.SITE + link);
                        } else
                            openPage(link);
                    } else {
                        openPage(link);
                    }
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

    private boolean isAds(int pos) {
        if (tab == 2) {
            if (pos < 2) { //back
                if (pos == 1) //clear
                    ads.clear();
                tabHost.setCurrentTab(1);
                tabHost.setCurrentTab(0);
                return true;
            }
            ads.setIndex(pos);
            ads.showAd(adMain.getItem(pos).getTitle(),
                    adMain.getItem(pos).getLink(),
                    adMain.getItem(pos).getHead(0));
            adMain.getItem(pos).setDes("");
            adMain.notifyDataSetChanged();
            return true;
        }
        if (tabHost.getCurrentTab() == 0 && pos == 0) {
            tab = 2;
            showDevads();
            return true;
        }
        return false;
    }

    private void showDevads() {
        try {
            adMain.clear();
            ads.loadList(adMain, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        ListItem item = new ListItem(getResources().getString(R.string.back_title));
        if (adMain.getCount() == 0) {
            item.setDes(getResources().getString(R.string.list_is_empty));
            adMain.addItem(item);
        } else {
            item.setDes(getResources().getString(R.string.back_des));
            adMain.insertItem(0, item);
            item = new ListItem(getResources().getString(R.string.clear));
            item.setDes(getResources().getString(R.string.this_list));
            adMain.insertItem(1, item);
        }
        adMain.notifyDataSetChanged();
    }

    private void openList(File f, boolean loadIfNeed) {
        try {
            adMain.clear();
            if (act.status.checkTime(f.lastModified() / DateHelper.SEC_IN_MILLS))
                fabRefresh.setVisibility(View.GONE);
            else
                fabRefresh.setVisibility(View.VISIBLE);
            BufferedReader br = new BufferedReader(new FileReader(f));
            String t, d, l, h;
            int i = 0;
            if (tabHost.getCurrentTab() == 0) { //news
                tab = 0;
                adMain.addItem(new ListItem(getResources().getString(R.string.news_dev)), "");
                adMain.getItem(i++).addLink("");
            } else if (tabHost.getCurrentTab() == 1) { //main
                tab = 1;
                adMain.addItem(new ListItem(getResources().getString(R.string.novosti)), "");
                adMain.getItem(i++).addLink(FORUM);
            }
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
}
