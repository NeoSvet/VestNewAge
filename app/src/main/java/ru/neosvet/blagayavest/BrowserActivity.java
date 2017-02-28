package ru.neosvet.blagayavest;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;

import java.io.File;

import ru.neosvet.ui.StatusBar;
import ru.neosvet.ui.WebClient;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.LoaderTask;

public class BrowserActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {
    public static final String ARTICLE = "article", THEME = "theme",
            NOMENU = "nomenu", SCALE = "scale", FILE = "file://", PNG = ".png";
    private boolean bArticle, bNomenu, bTheme, bTwo = false;
    private SharedPreferences pref;
    private SharedPreferences.Editor editor;
    private LoaderTask loader = null;
    private WebView wvBrowser;
    private DataBase dbJournal;
    private StatusBar status;
    private View ivMenu;
    private DrawerLayout drawerMenu;
    private Lib lib;
    private String link;
    private MenuItem miTheme, miNomenu;


    public static void openActivity(Context context, String link, boolean article) {
        Intent intent = new Intent(context, BrowserActivity.class);
        intent.putExtra(DataBase.LINK, link.substring(Lib.LINK.length()));
        intent.putExtra(ARTICLE, article);
        if (!(context instanceof Activity)) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);
        initViews();
        setViews();
        restoreActivityState(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(Lib.TASK, loader);
        outState.putString(DataBase.LINK, link);
        super.onSaveInstanceState(outState);
    }

    private File getPage() {
        File f;
        if (bArticle)
            f = lib.getFile("/" + ARTICLE + "/" + link);
        else
            f = lib.getPageFile(link);
        return f;
    }

    private void restoreActivityState(Bundle state) {
        if (state == null) {
            link = getIntent().getStringExtra(DataBase.LINK);
            openLink(link);
        } else {
            loader = (LoaderTask) state.getSerializable(Lib.TASK);
            link = state.getString(DataBase.LINK);
            if (loader == null) {
                openLink(link);
            } else {
                loader.setAct(this);
                status.setLoad(true);
            }
        }
        miTheme.setVisible(!link.contains(PNG));
    }

    private void downloadPage(boolean bReplaceStyle) {
        wvBrowser.clearCache(true);
        restoreStyle();
        loader = new LoaderTask(this);
        status.setCrash(false);
        status.setLoad(true);
        if (bReplaceStyle)
            loader.execute(link, getPage().toString(), "");
        else
            loader.execute(link, getPage().toString());
    }

    private void initViews() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setVisibility(View.GONE);
        wvBrowser = (WebView) findViewById(R.id.wvBrowser);
        status = new StatusBar(this, findViewById(R.id.pStatus));
        ivMenu = findViewById(R.id.ivMenu);
        dbJournal = new DataBase(this);
        NavigationView navMenu = (NavigationView) findViewById(R.id.nav_view);
        navMenu.setNavigationItemSelectedListener(this);
//        miLight = navMenu.getMenu().getItem(2).getSubMenu().getItem(0);
//        miDark = navMenu.getMenu().getItem(2).getSubMenu().getItem(1);
        miNomenu = navMenu.getMenu().getItem(1);
        miTheme = navMenu.getMenu().getItem(2);
        drawerMenu = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerMenu, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerMenu.addDrawerListener(toggle);
        toggle.syncState();
        bArticle = getIntent().getBooleanExtra(ARTICLE, false);
        lib = new Lib(this);
        pref = getSharedPreferences(this.getLocalClassName(), MODE_PRIVATE);
        editor = pref.edit();
        bTheme = pref.getInt(THEME, 0) == 0;
        wvBrowser.getSettings().setBuiltInZoomControls(true);
        wvBrowser.getSettings().setDisplayZoomControls(false);
        int z = pref.getInt(SCALE, 0);
        if (z > 0)
            wvBrowser.setInitialScale(z);
        if (android.os.Build.VERSION.SDK_INT > 18) {
            wvBrowser.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    if (motionEvent.getPointerCount() == 2) {
                        bTwo = true;
                    } else if (bTwo) {
                        bTwo = false;
                        wvBrowser.setInitialScale((int) (wvBrowser.getScale() * 100.0));
                    }
                    return false;
                }
            });
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (wvBrowser.canGoBack()) {
            wvBrowser.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private void setViews() {
        wvBrowser.setWebViewClient(new WebClient(this));
        ivMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                drawerMenu.openDrawer(Gravity.LEFT);
            }
        });
        if (bTheme)
            setCheckItem(miTheme.getSubMenu().getItem(0), true);
        else
            setCheckItem(miTheme.getSubMenu().getItem(1), true);
        if (pref.getBoolean(NOMENU, false)) {
            bNomenu = true;
            setCheckItem(miNomenu, bNomenu);
            ivMenu.setVisibility(View.GONE);
        }
        status.setClick(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                status.onClick();
            }
        });
    }

    @Override
    protected void onStop() {
        editor.putInt(SCALE, (int) (wvBrowser.getScale() * 100.0));
        editor.apply();
        super.onStop();
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_refresh) {
            if (loader == null) {
                if (link.contains(PNG))
                    downloadFile(getFile());
                else
                    downloadPage(true);
            }
        } else if (id == R.id.nav_nomenu) {
            bNomenu = !bNomenu;
            setCheckItem(item, bNomenu);
            editor.putBoolean(NOMENU, bNomenu);
            if (bNomenu)
                ivMenu.setVisibility(View.GONE);
            else
                ivMenu.setVisibility(View.VISIBLE);
        } else if (id == R.id.nav_light || id == R.id.nav_dark) {
            if ((id == R.id.nav_light && bTheme)
                    || (id == R.id.nav_dark && !bTheme))
                return true;
            bTheme = !bTheme;
            setCheckItem(miTheme.getSubMenu().getItem(0), bTheme);
            setCheckItem(miTheme.getSubMenu().getItem(1), !bTheme);
            editor.putInt(THEME, (bTheme ? 0 : 1));
            wvBrowser.clearCache(true);
            openPage();
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void setCheckItem(MenuItem item, boolean check) {
        if (check) {
            item.setIcon(R.drawable.check);
        } else {
            item.setIcon(R.drawable.none);
        }
    }

    private void restoreStyle() {
        final File fStyle = lib.getFile(Lib.STYLE);
        if (fStyle.exists()) {
            final File fDark = lib.getFile(Lib.DARK);
            if (fDark.exists())
                fStyle.renameTo(lib.getFile(Lib.LIGHT));
            else
                fStyle.renameTo(fDark);
        }
    }

    public void openLink(String url) {
        link = url;
        if (url.contains(PNG)) {
            openFile();
        } else {
            if (getPage().exists())
                openPage();
            else
                downloadPage(false);
        }
    }

    public void newLink(String url) {
        if (url.contains(ARTICLE)) {
            bArticle = true;
            url = url.substring(ARTICLE.length() + 1);
        } else
            bArticle = false;
        if (!link.equals(url)) {
            link = url;
            miTheme.setVisible(!link.contains(PNG));
        }
    }

    public void openFile() {
        status.setLoad(false);
        File f = getFile();
        if (f.exists()) {
            wvBrowser.loadUrl(FILE + f.toString());
        } else {
            downloadFile(f);
        }
    }

    private File getFile() {
        return new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS).getAbsolutePath()
                .toString() + link.substring(link.lastIndexOf("/")));
    }

    private void downloadFile(File f) {
        loader = new LoaderTask(this);
        status.setLoad(true);
        loader.execute(Lib.SITE + link, f.toString());
    }

    public void openPage() {
        status.setLoad(false);
        final File fLight = lib.getFile(Lib.LIGHT);
        final File fDark = lib.getFile(Lib.DARK);
        final File fStyle = lib.getFile(Lib.STYLE);
        boolean b = true;
        if (fStyle.exists()) {
            b = (fDark.exists() && !bTheme) || (fLight.exists() && bTheme);
            if (b) {
                if (fDark.exists())
                    fStyle.renameTo(fLight);
                else
                    fStyle.renameTo(fDark);
            }
        }
        if (b) {
            if (bTheme)
                fLight.renameTo(fStyle);
            else
                fDark.renameTo(fStyle);
        }
        String page = getPage().toString();
        if (link.contains("#"))
            page += link.substring(link.indexOf("#"));
        wvBrowser.loadUrl(FILE + page);
    }

    public void addJournal() {
        ContentValues cv = new ContentValues();
        cv.put(DataBase.TIME, System.currentTimeMillis());
        cv.put(DataBase.TITLE, wvBrowser.getTitle());
        String s = (bArticle ? ARTICLE : "") + link;
        cv.put(DataBase.LINK, s);
        SQLiteDatabase db = dbJournal.getWritableDatabase();
        int i = db.update(DataBase.NAME, cv, DataBase.LINK + " = ?", new String[]{s});
        if (i == 0)
            db.insert(DataBase.NAME, null, cv);
        dbJournal.close();
    }

    public void openInApps(String url) {
        lib.openInApps(url);
    }

    public void finishLoad(boolean suc) {
        status.setLoad(false);
        loader = null;
        if (suc) {
            if (link.contains(PNG))
                openFile();
            else
                openPage();
        } else {
            status.setCrash(true);
        }
    }
}
