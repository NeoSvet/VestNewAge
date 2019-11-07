package ru.neosvet.vestnewage.activity;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.work.Data;
import androidx.work.WorkInfo;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import ru.neosvet.ui.StatusButton;
import ru.neosvet.ui.Tip;
import ru.neosvet.ui.dialogs.SetNotifDialog;
import ru.neosvet.utils.BackFragment;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
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
import ru.neosvet.vestnewage.helpers.NotificationHelper;
import ru.neosvet.vestnewage.helpers.PromHelper;
import ru.neosvet.vestnewage.helpers.UnreadHelper;
import ru.neosvet.vestnewage.model.LoaderModel;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private final byte STATUS_MENU = 0, STATUS_PAGE = 1, STATUS_EXIT = 2;
    private final String LOADER = "loader";
    public static boolean isFirst = false, isCountInMenu = false;
    public boolean isMenuMode = false;
    public int k_new = 0;
    private int first_fragment;
    private MenuFragment frMenu;
    private BackFragment curFragment;
    private FragmentManager myFragmentManager;
    public Lib lib = new Lib(this);
    private Tip menuDownload;
    private NavigationView navigationView;
    private DrawerLayout drawer;
    private TextView bDownloadIt;
    public StatusButton status;
    private PromHelper prom;
    private SharedPreferences pref;
    private UnreadHelper unread;
    private LoaderModel model;
    private int cur_id, tab = 0, statusBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        pref = getSharedPreferences(MainActivity.class.getSimpleName(), MODE_PRIVATE);
        int p = pref.getInt(Const.START_SCEEN, Const.SCREEN_CALENDAR);
        if (p == 0 && getResources().getInteger(R.integer.screen_mode)
                < getResources().getInteger(R.integer.screen_tablet_port)) {
            setContentView(R.layout.main_activity_nomenu);
            first_fragment = R.id.menu_fragment;
            isMenuMode = true;
        } else
            setContentView(R.layout.main_activity);
        if (p == Const.SCREEN_SUMMARY)
            first_fragment = R.id.nav_rss;
        else if (p == Const.SCREEN_CALENDAR || !isMenuMode)
            first_fragment = R.id.nav_calendar;

        myFragmentManager = getFragmentManager();
        status = new StatusButton(this, findViewById(R.id.pStatus));
        menuDownload = new Tip(this, findViewById(R.id.pDownload));
        unread = new UnreadHelper(this);
        initInterface();
        initModel();

        isCountInMenu = pref.getBoolean(Const.COUNT_IN_MENU, true);
        if (!isCountInMenu || isMenuMode) {
            prom = new PromHelper(this, findViewById(R.id.tvPromTime));
        } else if (navigationView != null) { //it is not tablet and land
            prom = new PromHelper(this, navigationView.getHeaderView(0)
                    .findViewById(R.id.tvPromTimeInMenu));
        }

        updateNew();
        restoreActivityState(savedInstanceState);
    }

    private void initModel() {
        model = ViewModelProviders.of(this).get(LoaderModel.class);
        model.getProgress().observe(this, new Observer<Data>() {
            @Override
            public void onChanged(@Nullable Data data) {

            }
        });
        model.getState().observe(this, new Observer<List<WorkInfo>>() {
            @Override
            public void onChanged(@Nullable List<WorkInfo> workInfos) {
                for (int i = 0; i < workInfos.size(); i++) {
                    if (workInfos.get(i).getState().isFinished())
                        finishLoad();
                    if (workInfos.get(i).getState().equals(WorkInfo.State.FAILED))
                        Lib.showToast(MainActivity.this, workInfos.get(i).getOutputData().getString(Const.ERROR));
                }
            }
        });
        //if (model.inProgress)
            //TODO show dialog
    }

    private void restoreActivityState(Bundle state) {
        if (state == null) {
            Intent intent = getIntent();
            tab = intent.getIntExtra(Const.TAB, 0);
            if (pref.getBoolean(Const.FIRST, true)) {
                SharedPreferences.Editor editor = pref.edit();
                editor.putBoolean(Const.FIRST, false);
                editor.apply();
                tab = -1;
                setFragment(R.id.nav_help);
                isFirst = true;
            } else {
                if (pref.getBoolean(Const.START_NEW, false) && k_new > 0)
                    setFragment(R.id.nav_new);
                else
                    setFragment(intent.getIntExtra(Const.CUR_ID, first_fragment));
            }
        } else {
            cur_id = state.getInt(Const.CUR_ID);
            if (navigationView == null && !isMenuMode)
                setMenuFragment();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        prom.stop();
        model.removeObserves(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prom != null)
            prom.resume();
    }

    public void setProm(View textView) {
        prom = new PromHelper(this, textView);
    }

    public void updateNew() {
        unread.open();
        k_new = unread.getCount();
        unread.close();
        if (navigationView != null)
            navigationView.getMenu().getItem(0).setIcon(unread.getNewId(k_new));
        else if (frMenu != null)
            frMenu.setNew(unread.getNewId(k_new));
    }

    private void initInterface() {
        findViewById(R.id.bDownloadAll).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                menuDownload.hide();
                model.startLoad(LoaderModel.DOWNLOAD_ALL, null);
            }
        });
        bDownloadIt = (TextView) findViewById(R.id.bDownloadIt);
        bDownloadIt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                menuDownload.hide();
                Data.Builder data = new Data.Builder();
                if (cur_id == R.id.nav_calendar) {
                    data.putInt(Const.YEAR, ((CalendarFragment) curFragment).getCurrentYear());
                    model.startLoad(LoaderModel.DOWNLOAD_YEAR, data.build());
                } else {
                    data.putInt(Const.SELECT, cur_id);
                    model.startLoad(LoaderModel.DOWNLOAD_ID, data.build());
                }
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
        if (!item.isChecked())
            setFragment(item.getItemId());
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    public void setCurFragment(BackFragment fragment) {
        curFragment = fragment;
    }

    public void setFrMenu(MenuFragment frMenu) {
        this.frMenu = frMenu;
        frMenu.setNew(unread.getNewId(k_new));
    }

    public void setFragment(int id) {
        statusBack = STATUS_PAGE;
        if (cur_id != id)
            isFirst = false;
        menuDownload.hide();
        cur_id = id;
        if (navigationView == null) {
            if (!isMenuMode)
                setMenuFragment();
        } else
            navigationView.setCheckedItem(id);
        status.setCrash(false);
        FragmentTransaction fragmentTransaction = myFragmentManager.beginTransaction();
        curFragment = null;
        if (isMenuMode && isCountInMenu && id != R.id.menu_fragment)
            prom.hide();
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
                if (getIntent().hasExtra(DataBase.ID)) {
                    int n = getIntent().getIntExtra(DataBase.ID, NotificationHelper.NOTIF_SUMMARY);
                    NotificationHelper notifHelper = new NotificationHelper(MainActivity.this);
                    for (int i = NotificationHelper.NOTIF_SUMMARY; i <= n; i++)
                        notifHelper.cancel(i);
                    getIntent().removeExtra(DataBase.ID);
                }
                break;
            case R.id.nav_main:
                SiteFragment frSite = new SiteFragment();
                frSite.setTab(tab);
                fragmentTransaction.replace(R.id.my_fragment, frSite);
                break;
            case R.id.nav_calendar:
                curFragment = new CalendarFragment();
                fragmentTransaction.replace(R.id.my_fragment, curFragment);
                break;
            case R.id.nav_book:
                BookFragment frBook = new BookFragment();
                frBook.setTab(tab);
                fragmentTransaction.replace(R.id.my_fragment, frBook);
                break;
            case R.id.nav_search:
                SearchFragment search = new SearchFragment();
                String s = getIntent().getStringExtra(Const.LINK);
                if (s != null) {
                    search.setString(s);
                    search.setPage(getIntent().getIntExtra(DataBase.SEARCH, 1));
                    search.setMode(tab);
                }
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
                    tab = 0;
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

    public void finishAllLoad(boolean suc, boolean all) {
        model.finish();
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.NeoDialog);
        if (suc) {
            if (all)
                builder.setMessage(getResources().getString(R.string.all_load_suc));
            else
                builder.setMessage(getResources().getString(R.string.it_load_suc));
        } else
            builder.setMessage(getResources().getString(R.string.load_fail));
        builder.setPositiveButton(getResources().getString(android.R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {

                    }
                });
        builder.create().show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (curFragment != null) {
            if (requestCode == CollectionsFragment.MARKER_REQUEST) {
                ((CollectionsFragment) curFragment).putResult(resultCode);
            }
            if (curFragment instanceof SettingsFragment) {
                if (resultCode == RESULT_OK)
                    if (requestCode == SetNotifDialog.RINGTONE)
                        ((SettingsFragment) curFragment).putRingtone(data);
                    else if (requestCode == SetNotifDialog.CUSTOM)
                        ((SettingsFragment) curFragment).putCustom(data);
            }
        }

    }

    @Override
    public void onBackPressed() {
        if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (isFirst) {
            setFragment(first_fragment);
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
            setFragment(R.id.menu_fragment);
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
            switch (cur_id) {
                case R.id.nav_main:
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
}
