package ru.neosvet.vestnewage.activity;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.work.Data;

import com.google.android.material.navigation.NavigationView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Timer;
import java.util.TimerTask;

import ru.neosvet.ui.MultiWindowSupport;
import ru.neosvet.ui.StatusButton;
import ru.neosvet.ui.Tip;
import ru.neosvet.ui.dialogs.CustomDialog;
import ru.neosvet.ui.dialogs.SetNotifDialog;
import ru.neosvet.utils.BackFragment;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.SlashUtils;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.fragment.BookFragment;
import ru.neosvet.vestnewage.fragment.CabmainFragment;
import ru.neosvet.vestnewage.fragment.CalendarFragment;
import ru.neosvet.vestnewage.fragment.CollectionsFragment;
import ru.neosvet.vestnewage.fragment.HelpFragment;
import ru.neosvet.vestnewage.fragment.JournalFragment;
import ru.neosvet.vestnewage.fragment.MenuFragment;
import ru.neosvet.vestnewage.fragment.NewFragment;
import ru.neosvet.vestnewage.fragment.SearchFragment;
import ru.neosvet.vestnewage.fragment.SettingsFragment;
import ru.neosvet.vestnewage.fragment.SiteFragment;
import ru.neosvet.vestnewage.fragment.SummaryFragment;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.LoaderHelper;
import ru.neosvet.vestnewage.helpers.NotificationHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.helpers.PromHelper;
import ru.neosvet.vestnewage.helpers.UnreadHelper;
import ru.neosvet.vestnewage.model.SlashModel;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, Observer<Data> {
    private final byte STATUS_MENU = 0, STATUS_PAGE = 1, STATUS_EXIT = 2;
    public static boolean isFirst = false, isCountInMenu = false;
    public boolean isMenuMode = false;
    private int first_fragment;
    private MenuFragment frMenu;
    private BackFragment curFragment;
    private FragmentManager myFragmentManager;
    public Lib lib = new Lib(this);
    private Tip menuDownload;
    private NavigationView navigationView;
    private DrawerLayout drawer;
    private TextView bDownloadIt, tvNew;
    public StatusButton status;
    private PromHelper prom;
    private SharedPreferences pref;
    private UnreadHelper unread;
    private int cur_id, prev_id = 0, tab = 0, statusBack, k_new = 0;
    public View fab;
    private SlashUtils slash;
    public Animation anMin, anMax;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        pref = getSharedPreferences(MainActivity.class.getSimpleName(), MODE_PRIVATE);
        int p = pref.getInt(Const.START_SCEEN, Const.SCREEN_CALENDAR);
        if (p == 0 && getResources().getInteger(R.integer.screen_mode)
                < getResources().getInteger(R.integer.screen_tablet_port)) {
            setContentView(R.layout.main_content);
            first_fragment = R.id.menu_fragment;
            isMenuMode = true;
        } else
            setContentView(R.layout.main_activity);
        if (p == Const.SCREEN_SUMMARY)
            first_fragment = R.id.nav_rss;
        else if (p == Const.SCREEN_CALENDAR || !isMenuMode)
            first_fragment = R.id.nav_calendar;

        if (savedInstanceState == null)
            initStar();
        else
            findViewById(R.id.ivStar).setVisibility(View.GONE);

        slash = new SlashUtils(MainActivity.this);
        SlashModel model = ViewModelProviders.of(this).get(SlashModel.class);
        if (slash.openLink(getIntent())) {
            tab = slash.getIntent().getIntExtra(Const.TAB, tab);
            first_fragment = slash.getIntent().getIntExtra(Const.CUR_ID, first_fragment);
        } else if (!SlashModel.inProgress && slash.isNeedLoad()) {
            slash.checkAdapterNewVersion();
            ProgressHelper.addObserver(this, this);
            model.startLoad();
        }

        myFragmentManager = getFragmentManager();
        status = new StatusButton(this, findViewById(R.id.pStatus));
        menuDownload = new Tip(this, findViewById(R.id.pDownload));
        unread = new UnreadHelper(this);
        initInterface();
        initAnim();

        isCountInMenu = pref.getBoolean(Const.COUNT_IN_MENU, true);
        if (!isCountInMenu || isMenuMode) {
            prom = new PromHelper(this, findViewById(R.id.tvPromTime));
        } else if (navigationView != null) { //it is not tablet and land
            prom = new PromHelper(this, navigationView.getHeaderView(0)
                    .findViewById(R.id.tvPromTimeInMenu));
        }
        restoreState(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (isInMultiWindowMode())
                MultiWindowSupport.resizeFloatTextView(tvNew, true);
        }
    }

    private void initStar() {
        Animation anStar = AnimationUtils.loadAnimation(this, R.anim.flash);
        anStar.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                findViewById(R.id.ivStar).setVisibility(View.GONE);
                if (isFirst) {
                    setFragment(R.id.nav_help, false);
                    SlashModel.inProgress = false;
                    isFirst = true;
                    return;
                }
                if (first_fragment != 0) {
                    setFragment(first_fragment, false);
                    if (SlashModel.inProgress) {
                        ProgressHelper.removeObservers(MainActivity.this);
                        if (curFragment != null)
                            curFragment.startLoad();
                        SlashModel.inProgress = false;
                    }
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        findViewById(R.id.ivStar).startAnimation(anStar);
    }

    private void initAnim() {
        anMin = AnimationUtils.loadAnimation(this, R.anim.minimize);
        anMin.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                tvNew.setVisibility(View.GONE);
                if (fab != null)
                    fab.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        anMax = AnimationUtils.loadAnimation(this, R.anim.maximize);
    }

    private void restoreState(Bundle state) {
        updateNew();
        if (state == null) {
            Intent intent = getIntent();
            tab = intent.getIntExtra(Const.TAB, tab);
            if (pref.getBoolean(Const.FIRST, true)) {
                SharedPreferences.Editor editor = pref.edit();
                editor.putBoolean(Const.FIRST, false);
                editor.apply();
                tab = -1;
                isFirst = true;
            } else {
                if (pref.getBoolean(Const.START_NEW, false) && k_new > 0)
                    first_fragment = R.id.nav_new;
                else
                    first_fragment = intent.getIntExtra(Const.CUR_ID, first_fragment);
            }
        } else {
            cur_id = state.getInt(Const.CUR_ID);
            if (navigationView == null && !isMenuMode)
                setMenuFragment();
        }
        if (state != null)
            tvNew.clearAnimation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (prom != null)
            prom.stop();
        if (SlashModel.inProgress)
            ProgressHelper.removeObservers(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prom != null)
            prom.resume();
        if (SlashModel.inProgress)
            ProgressHelper.addObserver(this, this);
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        super.onMultiWindowModeChanged(isInMultiWindowMode);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            MultiWindowSupport.resizeFloatTextView(tvNew, isInMultiWindowMode);
    }

    @Override
    public void onChanged(@Nullable Data data) {
        if (!SlashModel.inProgress || data == null)
            return;
        if (data.getBoolean(Const.TIME, false))
            slash.reInitProm();
        if (data.getBoolean(Const.DIALOG, false)) {
            showDialog(data.getString(Const.LINK),
                    data.getString(Const.TITLE));
        }
        if (data.getBoolean(Const.FINISH, false)) {
            ProgressHelper.removeObservers(this);
            if (curFragment != null) {
                SlashModel.inProgress = false;
                curFragment.startLoad();
            }
        }
    }

    private void showDialog(final String link, String title) {
        final CustomDialog alert = new CustomDialog(this);
        alert.setTitle(getResources().getString(R.string.new_page_title));
        alert.setMessage(String.format(getResources().getString(R.string.new_page_des), title));
        alert.setRightButton(getResources().getString(R.string.refresh), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, BrowserActivity.class);
                intent.putExtra(Const.LINK, link);
                intent.putExtra(Const.START, true);
                startActivity(intent);
                alert.dismiss();
            }
        });
        alert.show(null);
    }

    public void setProm(View textView) {
        prom = new PromHelper(this, textView);
    }

    public void updateNew() {
        unread.open();
        k_new = unread.getCount();
        unread.close();
        try {
            File file = new File(getFilesDir() + File.separator + Const.ADS);
            if (file.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(file));
                br.readLine(); //time
                String s;
                while ((s = br.readLine()) != null) {
                    if (s.equals("<e>"))
                        k_new++;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (navigationView != null)
            navigationView.getMenu().getItem(0).setIcon(unread.getNewId(k_new));
        else if (frMenu != null)
            frMenu.setNew(unread.getNewId(k_new));
        tvNew.setText(String.valueOf(k_new));
        if (setNew())
            tvNew.startAnimation(AnimationUtils.loadAnimation(this, R.anim.blink));
    }

    private void initInterface() {
        findViewById(R.id.bDownloadAll).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                menuDownload.hide();
                LoaderHelper.postCommand(MainActivity.this,
                        LoaderHelper.DOWNLOAD_ALL, "");
            }
        });
        bDownloadIt = (TextView) findViewById(R.id.bDownloadIt);
        bDownloadIt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                menuDownload.hide();
                if (cur_id == R.id.nav_calendar) {
                    LoaderHelper.postCommand(MainActivity.this,
                            LoaderHelper.DOWNLOAD_YEAR,
                            String.valueOf(((CalendarFragment) curFragment).getCurrentYear()));
                } else {
                    LoaderHelper.postCommand(MainActivity.this,
                            LoaderHelper.DOWNLOAD_ID,
                            String.valueOf(cur_id));
                }
            }
        });
        tvNew = (TextView) findViewById(R.id.tvNew);
        tvNew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!ProgressHelper.isBusy())
                    setFragment(R.id.nav_new, true);
            }
        });

        if (getResources().getInteger(R.integer.screen_mode) !=
                getResources().getInteger(R.integer.screen_tablet_land)) {
            Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
            if (isMenuMode) return;
            drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
            ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                    this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
            drawer.addDrawerListener(toggle);
            toggle.syncState();
            navigationView = (NavigationView) findViewById(R.id.nav_view);
            navigationView.setNavigationItemSelectedListener(this);
            navigationView.setCheckedItem(cur_id);

            navigationView.getHeaderView(0).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    lib.openInApps(Const.SITE.substring(0, Const.SITE.length() - 1), null);
//                    startActivity(Intent.createChooser(lib.openInApps(Const.SITE),
//                            getResources().getString(R.string.open)));
                }
            });
        }
    }

    private void setMenuFragment() {
        if (frMenu == null) {
            FragmentTransaction fragmentTransaction = myFragmentManager.beginTransaction();
            frMenu = new MenuFragment();
            frMenu.setSelect(cur_id);
            fragmentTransaction.replace(R.id.menu_fragment, frMenu).commit();
        } else
            frMenu.setSelect(cur_id);
        frMenu.setNew(unread.getNewId(k_new));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(Const.CUR_ID, cur_id);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //getMenuInflater().inflate(R.menu.menu_table, menu);
        MenuItem miDownloadAll = menu.add(getResources().getString(R.string.download_title));
        miDownloadAll.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        miDownloadAll.setIcon(R.drawable.download_button);
        return true;
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        drawer.closeDrawer(GravityCompat.START);
        if (checkBusy())
            return false;
        if (!item.isChecked())
            setFragment(item.getItemId(), false);
        return true;
    }

    public void setCurFragment(BackFragment fragment) {
        curFragment = fragment;
    }

    public void setFrMenu(MenuFragment frMenu) {
        this.frMenu = frMenu;
        frMenu.setNew(unread.getNewId(k_new));
    }

    public void setFragment(int id, boolean savePrev) {
        statusBack = STATUS_PAGE;
        isFirst = false;
        menuDownload.hide();
        if (savePrev)
            prev_id = cur_id;
        else
            prev_id = 0;
        cur_id = id;
        if (navigationView == null) {
            if (!isMenuMode)
                setMenuFragment();
        } else
            navigationView.setCheckedItem(id);
        status.setError(null);
        FragmentTransaction fragmentTransaction = myFragmentManager.beginTransaction();
        curFragment = null;
        if (isMenuMode && isCountInMenu && id != R.id.menu_fragment)
            prom.hide();
        setNew();
        switch (id) {
            case R.id.menu_fragment:
                statusBack = STATUS_MENU;
                frMenu = new MenuFragment();
                fragmentTransaction.replace(R.id.my_fragment, frMenu);
                if (isCountInMenu) prom.show();
                break;
            case R.id.nav_new:
                fragmentTransaction.replace(R.id.my_fragment, new NewFragment());
                if (frMenu != null)
                    frMenu.setSelect(R.id.nav_new);
                break;
            case R.id.nav_rss:
                curFragment = new SummaryFragment();
                fragmentTransaction.replace(R.id.my_fragment, curFragment);
                id = 0;
                if (getIntent().hasExtra(DataBase.ID)) {
                    id = getIntent().getIntExtra(DataBase.ID, NotificationHelper.NOTIF_SUMMARY);
                    getIntent().removeExtra(DataBase.ID);
                } else if (slash.getIntent().hasExtra(DataBase.ID)) {
                    id = slash.getIntent().getIntExtra(DataBase.ID, NotificationHelper.NOTIF_SUMMARY);
                    slash.getIntent().removeExtra(DataBase.ID);
                }
                if (id != 0) {
                    NotificationHelper notifHelper = new NotificationHelper(MainActivity.this);
                    for (int i = NotificationHelper.NOTIF_SUMMARY; i <= id; i++)
                        notifHelper.cancel(i);
                }
                break;
            case R.id.nav_site:
                curFragment = new SiteFragment();
                ((SiteFragment) curFragment).setTab(tab);
                fragmentTransaction.replace(R.id.my_fragment, curFragment);
                break;
            case R.id.nav_calendar:
                curFragment = new CalendarFragment();
                fragmentTransaction.replace(R.id.my_fragment, curFragment);
                break;
            case R.id.nav_book:
                curFragment = new BookFragment();
                ((BookFragment) curFragment).setTab(tab);
                fragmentTransaction.replace(R.id.my_fragment, curFragment);
                break;
            case R.id.nav_search:
                SearchFragment search = new SearchFragment();
                if (getIntent().hasExtra(Const.LINK)) {
                    search.setString(getIntent().getStringExtra(Const.LINK));
                    search.setPage(getIntent().getIntExtra(Const.SEARCH, 1));
                } else if (slash.getIntent().hasExtra(Const.LINK)) {
                    search.setString(slash.getIntent().getStringExtra(Const.LINK));
                    search.setPage(slash.getIntent().getIntExtra(Const.SEARCH, 1));
                }
                search.setMode(tab);
                curFragment = search;
                fragmentTransaction.replace(R.id.my_fragment, curFragment);
                break;
            case R.id.nav_journal:
                fragmentTransaction.replace(R.id.my_fragment, new JournalFragment());
                break;
            case R.id.nav_marker:
                curFragment = new CollectionsFragment();
                fragmentTransaction.replace(R.id.my_fragment, curFragment);
                break;
            case R.id.nav_cabinet:
                curFragment = new CabmainFragment();
                fragmentTransaction.replace(R.id.my_fragment, curFragment);
                break;
            case R.id.nav_settings:
                curFragment = new SettingsFragment();
                fragmentTransaction.replace(R.id.my_fragment, curFragment);
                break;
            case R.id.nav_help:
                if (tab == -1) { //first start
                    HelpFragment frHelp = new HelpFragment();
                    fragmentTransaction.replace(R.id.my_fragment, frHelp);
                    frHelp.setOpenHelp(0);
                } else
                    fragmentTransaction.replace(R.id.my_fragment, new HelpFragment());
                break;
        }
        tab = 0;
        fragmentTransaction.commit();
    }

    private boolean setNew() {
        if (k_new > 0 && (cur_id == R.id.nav_new || cur_id == R.id.nav_rss
                || cur_id == R.id.nav_site || cur_id == R.id.nav_calendar)) {
            tvNew.setVisibility(View.VISIBLE);
            return true;
        }
        tvNew.setVisibility(View.GONE);
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (curFragment == null || resultCode != RESULT_OK)
            return;
        if (curFragment instanceof CollectionsFragment) {
            CollectionsFragment fr = (CollectionsFragment) curFragment;
            if (requestCode == CollectionsFragment.MARKER_REQUEST)
                fr.putResult(resultCode);
            else
                fr.startModel(requestCode, data.getData());
            return;
        }
        if (curFragment instanceof SettingsFragment) {
            SettingsFragment fr = (SettingsFragment) curFragment;
            if (requestCode == SetNotifDialog.RINGTONE)
                fr.putRingtone(data);
            else if (requestCode == SetNotifDialog.CUSTOM)
                fr.putCustom(data);
        }
    }

    @Override
    public void onBackPressed() {
        if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (isFirst) {
            setFragment(first_fragment, false);
        } else if (prev_id != 0) {
            if (curFragment != null && !curFragment.onBackPressed())
                return;
            if (prev_id == R.id.nav_site)
                tab = 1;
            setFragment(prev_id, false);
        } else if (first_fragment == R.id.nav_new) {
            switch (pref.getInt(Const.START_SCEEN, Const.SCREEN_CALENDAR)) {
                case Const.SCREEN_MENU:
                    first_fragment = R.id.menu_fragment;
                    break;
                case Const.SCREEN_SUMMARY:
                    first_fragment = R.id.nav_rss;
                    break;
                default:
                    first_fragment = R.id.nav_calendar;
                    break;
            }
            setFragment(first_fragment, false);
        } else if (curFragment != null) {
            if (curFragment.onBackPressed())
                exit();
        } else
            exit();
    }

    private void exit() {
        if (statusBack == STATUS_EXIT) {
            super.onBackPressed();
        } else if (statusBack == STATUS_PAGE && isMenuMode) {
            statusBack = STATUS_MENU;
            setFragment(R.id.menu_fragment, false);
        } else { //  statusBack == STATUS_MENU;
            statusBack = STATUS_EXIT;
            Lib.showToast(this, getResources().getString(R.string.click_for_exit));
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    if (statusBack == STATUS_EXIT)
                        statusBack = STATUS_MENU;
                }
            }, 3 * DateHelper.SEC_IN_MILLS);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        showDownloadMenu();
        return super.onOptionsItemSelected(item);
    }

    public void showDownloadMenu() {
        if (menuDownload.isShow())
            menuDownload.hide();
        else {
            if (ProgressHelper.isBusy())
                return;
            switch (cur_id) {
                case R.id.nav_site:
                    bDownloadIt.setVisibility(View.VISIBLE);
                    bDownloadIt.setText(getResources().getString(R.string.download_it_main));
                    break;
                case R.id.nav_calendar:
                    bDownloadIt.setVisibility(View.VISIBLE);
                    bDownloadIt.setText(getResources().getString(R.string.download_it_calendar));
                    break;
                case R.id.nav_book:
                    bDownloadIt.setVisibility(View.VISIBLE);
                    bDownloadIt.setText(getResources().getString(R.string.download_it_book));
                    break;
                default:
                    bDownloadIt.setVisibility(View.GONE);
            }
            menuDownload.show();
        }
    }

    public void openBook(String link, boolean katren) {
        tab = katren ? 0 : 1;
        setFragment(R.id.nav_book, true);
        int year = 2016;
        try {
            link = link.substring(link.lastIndexOf("/") + 1, link.lastIndexOf("."));
            year = Integer.parseInt(link);
        } catch (Exception ignored) {
        }
        ((BookFragment) curFragment).setYear(year);
    }

    public void startAnimMin() {
        if (fab.getVisibility() == View.GONE)
            return;
        if (k_new > 0)
            tvNew.startAnimation(anMin);
        fab.startAnimation(anMin);
    }

    public void startAnimMax() {
        if (setNew())
            tvNew.startAnimation(anMax);
        fab.setVisibility(View.VISIBLE);
        fab.startAnimation(anMax);
    }

    public boolean checkBusy() {
        if (ProgressHelper.isBusy()) {
            Lib.showToast(this, getResources().getString(R.string.app_is_busy));
            return true;
        }
        return false;
    }
}
