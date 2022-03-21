package ru.neosvet.vestnewage.fragment;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.Data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import ru.neosvet.ui.NeoFragment;
import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.BrowserActivity;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.DevadsHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.list.ListAdapter;
import ru.neosvet.vestnewage.list.ListItem;
import ru.neosvet.vestnewage.model.SiteModel;

public class SiteFragment extends NeoFragment implements Observer<Data> {
    private final int TAB_NEWS = 0, TAB_MAIN = 1, TAB_DEV = 2;
    public static final String MAIN = "/main", NEWS = "/news", FORUM = "intforum.html", NOVOSTI = "novosti.html", END = "<end>";
    private ListAdapter adMain;
    private View fabRefresh, tvEmptySite;
    private SiteModel model;
    private TabHost tabHost;
    private ListView lvMain;
    private DevadsHelper ads;
    private int x, y, tab = TAB_NEWS;
    private boolean notClick = false, scrollToFirst = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.site_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ads = new DevadsHelper(act);
        initViews(view);
        setViews();
        model = new ViewModelProvider(this).get(SiteModel.class);
        initTabs();
        restoreState(savedInstanceState);
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
    }

    private void initLoad() {
        fabRefresh.setVisibility(View.GONE);
        act.status.setLoad(true);
        ProgressHelper.addObserver(act, this);
    }

    @Override
    public void onChanged(@Nullable Data data) {
        if (!ProgressHelper.isBusy() || data == null)
            return;
        if (data.getBoolean(Const.START, false)) {
            act.status.loadText();
            return;
        }
        if (data.getBoolean(Const.DIALOG, false)) {
            act.status.setProgress(data.getInt(Const.PROG, 0));
            return;
        }
        fabRefresh.setVisibility(View.VISIBLE);
        act.status.setLoad(false);
        ProgressHelper.setBusy(false);
        if (data.getBoolean(Const.ADS, false)) {
            showDevads();
            return;
        }
        if (data.getBoolean(Const.LIST, false)) {
            String f = data.getString(Const.FILE);
            if (tab != TAB_DEV && tabHost.getCurrentTabTag().equals(f))
                openList(getFile(), false);
            return;
        }
        if (data.getBoolean(Const.FINISH, false)) {
            String error = data.getString(Const.ERROR);
            ProgressHelper.removeObservers(act);
            if (error != null) {
                act.status.setError(error);
                if (adMain.getCount() == 0) {
                    tab = TAB_DEV;
                    showDevads();
                }
                return;
            }

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
        } else if (getArguments() != null)
            tab = getArguments().getInt(Const.TAB);
        else
            tab = TAB_NEWS;
        switch (tab) {
            case TAB_NEWS:
                tabHost.setCurrentTab(0);
                break;
            case TAB_MAIN:
                tabHost.setCurrentTab(0);
                tabHost.setCurrentTab(1);
                break;
            case TAB_DEV:
                tabHost.setCurrentTab(0);
                tab = TAB_DEV;
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
        tabHost.setup();
        TabHost.TabSpec tabSpec;

        tabSpec = tabHost.newTabSpec(NEWS);
        tabSpec.setIndicator(getString(R.string.news),
                ContextCompat.getDrawable(act, R.drawable.none));
        tabSpec.setContent(R.id.lvMain);
        tabHost.addTab(tabSpec);

        tabSpec = tabHost.newTabSpec(MAIN);
        tabSpec.setIndicator(getString(R.string.site),
                ContextCompat.getDrawable(act, R.drawable.none));
        tabSpec.setContent(R.id.lvMain);
        tabHost.addTab(tabSpec);

        TabWidget widget = tabHost.getTabWidget();
        for (int i = 0; i < widget.getChildCount(); i++) {
            View v = widget.getChildAt(i);
            TextView tv = v.findViewById(android.R.id.title);
            if (tv != null) {
                tv.setMaxLines(1);
                v.setBackgroundResource(R.drawable.table_selector);
            }
        }
        tabHost.setCurrentTab(1);
        tabHost.setOnTabChangedListener(name -> {
            if (name.equals(MAIN)) {
                act.setTitle(getString(R.string.site));
                tab = TAB_MAIN;
            } else {
                act.setTitle(getString(R.string.news));
                tab = TAB_NEWS;
            }
            File f = getFile();
            if (f.exists())
                openList(f, true);
            else
                startLoad();
        });
    }

    private void initViews(View container) {
        tabHost = container.findViewById(R.id.thMain);
        tvEmptySite = container.findViewById(R.id.tvEmptySite);
        fabRefresh = container.findViewById(R.id.fabRefresh);
        act.fab = fabRefresh;
        lvMain = container.findViewById(R.id.lvMain);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setViews() {
        fabRefresh.setOnClickListener(view -> startLoad());
        adMain = new ListAdapter(act);
        lvMain.setAdapter(adMain);
        lvMain.setOnItemClickListener((adapterView, view, pos, l) -> {
            if (notClick) return;
            if (act.checkBusy()) return;
            if (isAds(pos))
                return;
            if (adMain.getItem(pos).getCount() == 1) {
                openSingleLink(adMain.getItem(pos).getLink());
            } else {
                openMultiLink(adMain.getItem(pos), view);
            }
        });
        lvMain.setOnTouchListener((v, event) -> {
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
        });
        act.status.setClick(view -> onStatusClick(false));
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

    @Override
    public void onStatusClick(boolean reset) {
        fabRefresh.setVisibility(View.VISIBLE);
        ProgressHelper.cancelled();
        ProgressHelper.setBusy(false);
        if (!act.status.isStop()) {
            act.status.setLoad(false);
            return;
        }
        if (reset) {
            act.status.setError(null);
            return;
        }
        if (!act.status.onClick() && act.status.isTime())
            startLoad();
    }

    private void openMultiLink(ListItem links, View parent) {
        PopupMenu pMenu = new PopupMenu(act, parent);
        for (int i = 1; i < links.getCount(); i++)
            pMenu.getMenu().add(links.getHead(i));
        pMenu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            for (int i = 0; i < links.getCount(); i++) {
                if (links.getHead(i).equals(title)) {
                    openPage(links.getLink(i));
                    break;
                }
            }
            return true;
        });
        pMenu.show();
    }

    private void openSingleLink(String link) {
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

    private boolean isAds(int pos) {
        if (tab == 2) {
            if (pos == 0) { //back
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
            ads.reopen();
            ads.loadList(adMain, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        ListItem item = new ListItem(getString(R.string.back_title));
        item.setDes(getString(R.string.back_des));
        adMain.insertItem(0, item);
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
            if (tab == TAB_NEWS) {
                adMain.addItem(new ListItem(getString(R.string.news_dev)), "");
                adMain.getItem(i++).addLink("");
            } else if (tab == TAB_MAIN) {
                adMain.addItem(new ListItem(getString(R.string.novosti)), "");
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
                startLoad();
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
                act.lib.openInApps(url, getString(R.string.to_load));
            } else {
                act.lib.openInApps(url, null);
            }
        } else {
            BrowserActivity.openReader(act, url, null);
        }
    }

    @Override
    public void startLoad() {
        if (ProgressHelper.isBusy())
            return;
        initLoad();
        act.status.startText();
        if (tab == TAB_DEV) {
            model.loadAds();
            return;
        }
        String url = Const.SITE;
        if (tab == TAB_NEWS)
            url += NOVOSTI;
        model.startLoad(url, getFile().toString());
    }

    private File getFile() {
        return new File(act.getFilesDir() + tabHost.getCurrentTabTag());
    }
}
