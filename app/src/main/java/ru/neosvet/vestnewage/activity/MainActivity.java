package ru.neosvet.vestnewage.activity;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
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
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.work.Data;

import java.util.Timer;
import java.util.TimerTask;

import ru.neosvet.ui.MultiWindowSupport;
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

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, Observer<Data> {
    private final byte STATUS_MENU = 0, STATUS_PAGE = 1, STATUS_EXIT = 2;
    public static boolean isFirst = false, isCountInMenu = false;
    public boolean isMenuMode = false;
    private int first_fragment;
    private MenuFragment frMenu;
    private BackFragment curFragment, prevFragment;
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
    private LoaderModel model;
    private int cur_id, tab = 0, statusBack, k_new = 0;
    public View fab;
    public Animation anMin, anMax;
    private Handler updateDialog;

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
        initAnim();

        isCountInMenu = pref.getBoolean(Const.COUNT_IN_MENU, true);
        if (!isCountInMenu || isMenuMode) {
            prom = new PromHelper(this, findViewById(R.id.tvPromTime));
        } else if (navigationView != null) { //it is not tablet and land
            prom = new PromHelper(this, navigationView.getHeaderView(0)
                    .findViewById(R.id.tvPromTimeInMenu));
        }

        updateNew();
        restoreState(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (isInMultiWindowMode())
                MultiWindowSupport.resizeFloatTextView(tvNew, true);
        }
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

    private void initModel() {
        model = ViewModelProviders.of(this).get(LoaderModel.class);
        model.getProgress().observe(this, this);
        if (model.inProgress)
            initLoad();
    }

    @Override
    public void onChanged(@Nullable Data data) {
        switch (data.getInt(Const.DIALOG, -1)) {
            case LoaderModel.DIALOG_SHOW:
                model.showDialog(MainActivity.this);
                break;
            case LoaderModel.DIALOG_UPDATE:
                model.setProgMax(data.getInt(Const.MAX, 0));
                model.setProgMsg(data.getString(Const.MSG));
                model.showDialog(MainActivity.this);
                break;
            case LoaderModel.DIALOG_UP:
                model.upProg();
                break;
            case LoaderModel.DIALOG_MSG:
                model.setProgMsg(data.getString(Const.MSG));
                break;
        }
        if (data.getBoolean(Const.FINISH, false)) {
            boolean all = data.getInt(Const.MODE, 0) == LoaderModel.ALL;
            finishLoad(all, data.getString(Const.ERROR));
        }
    }

    private void restoreState(Bundle state) {
        if (state == null) {
            Intent intent = getIntent();
            tab = intent.getIntExtra(Const.TAB, 0);
            if (pref.getBoolean(Const.FIRST, true)) {
                SharedPreferences.Editor editor = pref.edit();
                editor.putBoolean(Const.FIRST, false);
                editor.apply();
                tab = -1;
                setFragment(R.id.nav_help, false);
                isFirst = true;
            } else {
                if (pref.getBoolean(Const.START_NEW, false) && k_new > 0)
                    setFragment(R.id.nav_new, false);
                else
                    setFragment(intent.getIntExtra(Const.CUR_ID, first_fragment), false);
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
        model.removeObservers(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        model.addObserver(this, this);
        if (prom != null)
            prom.resume();
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        super.onMultiWindowModeChanged(isInMultiWindowMode);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            MultiWindowSupport.resizeFloatTextView(tvNew, isInMultiWindowMode);
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
        if (k_new == 0)
            tvNew.setVisibility(View.GONE);
        else {
            tvNew.setVisibility(View.VISIBLE);
            tvNew.setText(String.valueOf(k_new));
            tvNew.startAnimation(AnimationUtils.loadAnimation(this, R.anim.blink));
        }
    }

    private void initLoad() {
        model.showDialog(this);
        if (updateDialog == null) {
            updateDialog = new Handler(new Handler.Callback() {
                @Override
                public boolean handleMessage(Message message) {
                    model.updateDialog();
                    return false;
                }
            });
        }
        new Thread(new Runnable() {
            public void run() {
                try {
                    while (model.inProgress) {
                        Thread.sleep(DateHelper.SEC_IN_MILLS);
                        updateDialog.sendEmptyMessage(0);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Lib.LOG("timer loader error: " + e.getMessage());
                }
            }
        }).start();
    }

    private void initInterface() {
        findViewById(R.id.bDownloadAll).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                menuDownload.hide();
                if (isBusy())
                    return;
                model.startLoad(LoaderModel.DOWNLOAD_ALL, "");
                initLoad();
            }
        });
        bDownloadIt = (TextView) findViewById(R.id.bDownloadIt);
        bDownloadIt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                menuDownload.hide();
                if (isBusy())
                    return;
                if (cur_id == R.id.nav_calendar) {
                    model.startLoad(LoaderModel.DOWNLOAD_YEAR, String.valueOf(
                            ((CalendarFragment) curFragment).getCurrentYear()));
                } else {
                    model.startLoad(LoaderModel.DOWNLOAD_ID, String.valueOf(cur_id));
                }
                initLoad();
            }
        });
        tvNew = (TextView) findViewById(R.id.tvNew);
        tvNew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
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

    private boolean isBusy() {
        if (curFragment == null || curFragment.model == null)
            return false;
        return curFragment.model.inProgress;
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
            setFragment(item.getItemId(), false);
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

    public void setFragment(int id, boolean savePrev) {
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
        status.setError(null);
        FragmentTransaction fragmentTransaction = myFragmentManager.beginTransaction();
        if (savePrev)
            prevFragment = curFragment;
        else
            prevFragment = null;
        curFragment = null;
        if (isMenuMode && isCountInMenu && id != R.id.menu_fragment)
            prom.hide();
        switch (id) {
            case R.id.menu_fragment:
                statusBack = STATUS_MENU;
                frMenu = new MenuFragment();
                fragmentTransaction.replace(R.id.my_fragment, frMenu);
                if (isCountInMenu) prom.show();
                setNew();
                break;
            case R.id.nav_new:
                fragmentTransaction.replace(R.id.my_fragment, new NewFragment());
                if (frMenu != null)
                    frMenu.setSelect(R.id.nav_new);
                setNew();
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
                setNew();
                break;
            case R.id.nav_main:
                curFragment = new SiteFragment();
                ((SiteFragment) curFragment).setTab(tab);
                fragmentTransaction.replace(R.id.my_fragment, curFragment);
                setNew();
                break;
            case R.id.nav_calendar:
                curFragment = new CalendarFragment();
                fragmentTransaction.replace(R.id.my_fragment, curFragment);
                setNew();
                break;
            case R.id.nav_book:
                curFragment = new BookFragment();
                ((BookFragment) curFragment).setTab(tab);
                fragmentTransaction.replace(R.id.my_fragment, curFragment);
                tvNew.setVisibility(View.GONE);
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
                tvNew.setVisibility(View.GONE);
                break;
            case R.id.nav_journal:
                fragmentTransaction.replace(R.id.my_fragment, new JournalFragment());
                tvNew.setVisibility(View.GONE);
                break;
            case R.id.nav_marker:
                curFragment = new CollectionsFragment();
                fragmentTransaction.replace(R.id.my_fragment, curFragment);
                tvNew.setVisibility(View.GONE);
                break;
            case R.id.nav_cabinet:
                curFragment = new CabmainFragment();
                fragmentTransaction.replace(R.id.my_fragment, curFragment);
                tvNew.setVisibility(View.GONE);
                break;
            case R.id.nav_settings:
                curFragment = new SettingsFragment();
                fragmentTransaction.replace(R.id.my_fragment, curFragment);
                tvNew.setVisibility(View.GONE);
                break;
            case R.id.nav_help:
                if (tab == -1) { //first start
                    HelpFragment frHelp = new HelpFragment();
                    fragmentTransaction.replace(R.id.my_fragment, frHelp);
                    frHelp.setOpenHelp(0);
                } else
                    fragmentTransaction.replace(R.id.my_fragment, new HelpFragment());
                tvNew.setVisibility(View.GONE);
                break;
        }

        tab = 0;
        fragmentTransaction.commit();
    }

    private void setNew() {
        if (k_new > 0)
            tvNew.setVisibility(View.VISIBLE);
        else
            tvNew.setVisibility(View.GONE);
    }

    public void finishLoad(boolean all, String error) {
        model.dismissDialog();
        model.finish();
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.NeoDialog);
        if (error == null) {
            if (all)
                builder.setMessage(getResources().getString(R.string.all_load_suc));
            else
                builder.setMessage(getResources().getString(R.string.it_load_suc));
        } else
            builder.setTitle(getResources().getString(R.string.error)).setMessage(error);
        builder.setPositiveButton(getResources().getString(android.R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {

                    }
                });
        builder.create().show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (curFragment == null)
            return;
        if (requestCode == CollectionsFragment.MARKER_REQUEST) {
            ((CollectionsFragment) curFragment).putResult(resultCode);
            return;
        }
        if (curFragment instanceof SettingsFragment) {
            if (resultCode == RESULT_OK) {
                SettingsFragment fr = (SettingsFragment) curFragment;
                if (requestCode == SetNotifDialog.RINGTONE)
                    fr.putRingtone(data);
                else if (requestCode == SetNotifDialog.CUSTOM)
                    fr.putCustom(data);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (isFirst) {
            setFragment(first_fragment, false);
        } else if (prevFragment != null) {
            if (curFragment != null && !curFragment.onBackPressed())
                return;
            curFragment = prevFragment;
            FragmentTransaction fragmentTransaction = myFragmentManager.beginTransaction();
            fragmentTransaction.replace(R.id.my_fragment, curFragment);
            fragmentTransaction.commit();
            prevFragment = null;
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

    public void openBook(String link, boolean katren) {
        tab = katren ? 0 : 1;
        setFragment(R.id.nav_book, true);
        int year = 0;
        try {
            link = link.substring(link.length() - 5, link.length() - 1);
            year = Integer.parseInt(link);
        } catch (Exception e) {
        }
        ((BookFragment) curFragment).setYear(year);
    }

    public void startAnimMin() {
        if (k_new > 0)
            tvNew.startAnimation(anMin);
        fab.startAnimation(anMin);
    }

    public void startAnimMax() {
        if (k_new > 0) {
            tvNew.setVisibility(View.VISIBLE);
            tvNew.startAnimation(anMax);
        }
        fab.setVisibility(View.VISIBLE);
        fab.startAnimation(anMax);
    }
}
