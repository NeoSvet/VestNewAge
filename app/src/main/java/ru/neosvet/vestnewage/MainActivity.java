package ru.neosvet.vestnewage;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
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

import java.util.Timer;
import java.util.TimerTask;

import ru.neosvet.ui.StatusBar;
import ru.neosvet.ui.Tip;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.LoaderTask;
import ru.neosvet.utils.Prom;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    public static boolean boolFirst = false;
    private CalendarFragment frCalendar;
    private CollectionsFragment frCollections;
    private CabmainFragment frCabinet;
    private boolean boolLoad = false, boolExit = false;
    private LoaderTask loader = null;
    private FragmentManager myFragmentManager;
    private final String LOADER = "loader";
    public static final String CUR_ID = "cur_id", TAB = "tab";
    public Lib lib = new Lib(this);
    private Tip menuDownload;
    private NavigationView navigationView;
    private DrawerLayout drawer;
    private View bDownloadIt;
    public StatusBar status;
    private Prom prom;
    private int cur_id, tab = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        myFragmentManager = getFragmentManager();
        status = new StatusBar(this, findViewById(R.id.pStatus));
        menuDownload = new Tip(this, findViewById(R.id.pDownload));

        if (savedInstanceState == null) {
            SharedPreferences pref = getSharedPreferences(this.getLocalClassName(), MODE_PRIVATE);
            Intent intent = getIntent();
            boolLoad = intent.getBooleanExtra(SummaryFragment.RSS, false);
            tab = intent.getIntExtra(TAB, 0);
            if (boolLoad) {
                setFragment(R.id.nav_rss);
            } else {
                if (pref.getBoolean(Lib.FIRST, true)) {
                    SharedPreferences.Editor editor = pref.edit();
                    editor.putBoolean(Lib.FIRST, false);
                    editor.apply();
                    tab = -1;
                    setFragment(R.id.nav_help);
                    boolFirst = true;
                } else
                    setFragment(intent.getIntExtra(CUR_ID, R.id.nav_calendar));
            }
        } else {
            cur_id = savedInstanceState.getInt(CUR_ID);
            loader = (LoaderTask) savedInstanceState.getSerializable(LOADER);
            if (loader != null && loader.getStatus() == AsyncTask.Status.RUNNING)
                loader.setAct(this);
        }
        initInterface();
        prom = new Prom(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        prom.stop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        prom.resume();
    }

    private void initInterface() {
        if (getResources().getInteger(R.integer.screen_mode) ==
                getResources().getInteger(R.integer.screen_tablet_land)) {
            if (!boolLoad)
                setMenuFragment();
        } else {
            Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
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
                    lib.openInApps(Lib.SITE.substring(0, Lib.SITE.length() - 1), null);
//                    startActivity(Intent.createChooser(lib.openInApps(Lib.SITE),
//                            getResources().getString(R.string.open)));
                }
            });
        }
        findViewById(R.id.bDownloadAll).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                menuDownload.hide();
                loader = new LoaderTask(MainActivity.this);
                loader.execute();
            }
        });
        bDownloadIt = findViewById(R.id.bDownloadIt);
        bDownloadIt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                menuDownload.hide();
                loader = new LoaderTask(MainActivity.this);
                loader.execute(String.valueOf(cur_id));
            }
        });
    }

    private void setMenuFragment() {
        FragmentTransaction fragmentTransaction = myFragmentManager.beginTransaction();
        MenuFragment mf = new MenuFragment();
        mf.setSelect(cur_id);
        fragmentTransaction.replace(R.id.menu_fragment, mf).commit();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(LOADER, loader);
        outState.putInt(CUR_ID, cur_id);
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

    public void setFrCalendar(CalendarFragment frCalendar) {
        this.frCalendar = frCalendar;
    }

    public void setFrCollections(CollectionsFragment frCollections) {
        this.frCollections = frCollections;
    }

    public void setFragment(int id) {
        if (cur_id != id)
            boolFirst = false;
        menuDownload.hide();
        cur_id = id;
        if (navigationView != null) //tut
            navigationView.setCheckedItem(id);
        status.setCrash(false);
        FragmentTransaction fragmentTransaction = myFragmentManager.beginTransaction();
        frCalendar = null;
        switch (id) {
            case R.id.nav_rss:
                SummaryFragment frSummary = new SummaryFragment();
                frSummary.setLoad(boolLoad);
                fragmentTransaction.replace(R.id.my_fragment, frSummary);
                boolLoad = false;
                break;
            case R.id.nav_main:
                SiteFragment frSite = new SiteFragment();
                frSite.setTab(tab);
                fragmentTransaction.replace(R.id.my_fragment, frSite);
                break;
            case R.id.nav_calendar:
                frCalendar = new CalendarFragment();
                if (tab > 0)
                    frCalendar.setNew(tab - 1);
                fragmentTransaction.replace(R.id.my_fragment, frCalendar);
                break;
            case R.id.nav_book:
                BookFragment frBook = new BookFragment();
                frBook.setTab(tab);
                fragmentTransaction.replace(R.id.my_fragment, frBook);
                break;
            case R.id.nav_search:
                SearchFragment frSearch = new SearchFragment();
                String s = getIntent().getStringExtra(DataBase.LINK);
                if (s != null) {
                    frSearch.setString(s);
                    frSearch.setPage(getIntent().getIntExtra(DataBase.PLACE, 1));
                    frSearch.setMode(tab);
                }
                fragmentTransaction.replace(R.id.my_fragment, frSearch);
                break;
            case R.id.nav_journal:
                fragmentTransaction.replace(R.id.my_fragment, new JournalFragment());
                break;
            case R.id.nav_marker:
                frCollections = new CollectionsFragment();
                fragmentTransaction.replace(R.id.my_fragment, frCollections);
                break;
            case R.id.nav_cabinet:
                frCabinet = new CabmainFragment();
                fragmentTransaction.replace(R.id.my_fragment, frCabinet);
                break;
            case R.id.nav_settings:
                fragmentTransaction.replace(R.id.my_fragment, new SettingsFragment());
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
        loader = null;
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
        super.onActivityResult(requestCode, resultCode, data);
        if (frCollections != null) {
            if (requestCode == frCollections.MARKER_REQUEST)
                frCollections.putResult(resultCode);
        }
    }

    @Override
    public void onBackPressed() {
        if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (boolFirst) {
            setFragment(R.id.nav_calendar);
            if (drawer == null)
                setMenuFragment();
        } else if (frCalendar != null) {
            if (frCalendar.onBackPressed())
                exit();
        } else if (frCollections != null) {
            if (frCollections.onBackPressed())
                exit();
        } else if (frCabinet != null) {
            if (frCabinet.onBackPressed())
                exit();
        } else {
            exit();
        }
    }

    private void exit() {
        if (boolExit) {
            super.onBackPressed();
        } else {
            boolExit = true;
            Lib.showToast(this, getResources().getString(R.string.click_for_exit));
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    boolExit = false;
                }
            }, 3000);
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
            if (cur_id == R.id.nav_rss || cur_id == R.id.nav_main || cur_id == R.id.nav_calendar || cur_id == R.id.nav_book)
                bDownloadIt.setVisibility(View.VISIBLE);
            else
                bDownloadIt.setVisibility(View.GONE);
            menuDownload.show();
        }
    }
}
