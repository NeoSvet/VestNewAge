package ru.neosvet.vestnewage;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
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
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.WebView;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ru.neosvet.ui.StatusBar;
import ru.neosvet.ui.Tip;
import ru.neosvet.ui.WebClient;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.LoaderTask;
import ru.neosvet.utils.Prom;

public class BrowserActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {
    public static final String THEME = "theme", NOMENU = "nomenu",
            SCALE = "scale", FILE = "file://", PNG = ".png",
            STYLE = "/style/style.css", PAGE = "/page.html";
    private final int CODE_OPEN = 1, CODE_DOWNLOAD = 2;
    private List<String> history = new ArrayList<String>();
    private boolean bNomenu, bTheme, bTwo = false, boolBack = false;
    private SharedPreferences pref;
    private SharedPreferences.Editor editor;
    private LoaderTask loader = null;
    private WebView wvBrowser;
    private DataBase dbPage, dbJournal;
    private TextView tvPromTime, tvPlace;
    private StatusBar status;
    private View fabMenu, pSearch;
    private DrawerLayout drawerMenu;
    private Lib lib;
    private String link = "";
    private String[] place;
    private int iPlace = -1;
    private Prom prom;
    private Animation anMin, anMax;
    private MenuItem miTheme, miNomenu;
    private Tip tip;


    public static void openPage(Context context, String link, String place) {
        Intent intent = new Intent(context, BrowserActivity.class);
        if (link.contains(Lib.LINK))
            intent.putExtra(DataBase.LINK, link.substring(Lib.LINK.length()));
        else
            intent.putExtra(DataBase.LINK, link);
        intent.putExtra(DataBase.PLACE, place);
        if (!(context instanceof Activity))
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.browser_activity);
        initViews();
        setViews();
        restoreActivityState(savedInstanceState);
        initPlace();
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

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(Lib.TASK, loader);
        outState.putString(DataBase.LINK, link);
        outState.putInt(DataBase.PLACE, iPlace);
        super.onSaveInstanceState(outState);
    }

    private void restoreActivityState(Bundle state) {
        if (state == null) {
            openLink(getIntent().getStringExtra(DataBase.LINK));
        } else {
            loader = (LoaderTask) state.getSerializable(Lib.TASK);
            link = state.getString(DataBase.LINK);
            dbPage = new DataBase(this, link);
            if (loader == null) {
                openPage(false);
            } else {
                loader.setAct(this);
                status.setLoad(true);
            }
            iPlace = state.getInt(DataBase.PLACE, 0);
        }
        miTheme.setVisible(!link.contains(PNG));
    }

    private void initPlace() {
        String p = getIntent().getStringExtra(DataBase.PLACE);
        if (p.length() == 0)
            return;
        if (iPlace == -1)
            iPlace = 0;
        fabMenu.setVisibility(View.GONE);
        tvPlace = (TextView) findViewById(R.id.tvPlace);
        View bPrev = findViewById(R.id.bPrev);
        View bNext = findViewById(R.id.bNext);
        if (p.contains("\n\n")) {
            place = p.split("\n\n");
            bPrev.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (iPlace > 0) {
                        iPlace--;
                        setPlace();
                    } else
                        tip.show();
                }
            });
            bNext.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (iPlace < place.length - 1) {
                        iPlace++;
                        setPlace();
                    } else
                        tip.show();
                }
            });
        } else {
            place = new String[]{p};
            bPrev.setVisibility(View.GONE);
            bNext.setVisibility(View.GONE);
        }
        findViewById(R.id.bClose).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                closeSearch();
            }
        });
        pSearch.setVisibility(View.VISIBLE);
    }

    private void closeSearch() {
        tip.hide();
        iPlace = -1;
        place = null;
        getIntent().putExtra(DataBase.PLACE, "");
        pSearch.setVisibility(View.GONE);
        if (!bNomenu) {
            fabMenu.setVisibility(View.VISIBLE);
            fabMenu.startAnimation(anMax);
        }
        wvBrowser.clearMatches();
    }

    public void setPlace() {
        if (iPlace == -1) return;
        String s = place[iPlace];
        tvPlace.setText(s.replace(Lib.N, " "));
        if (android.os.Build.VERSION.SDK_INT > 15)
            wvBrowser.findAllAsync(s);
        else {
            if (s.contains(Lib.N))
                s = s.substring(0, s.indexOf(Lib.N));
            wvBrowser.findAll(s);
            try {
                //Can't use getMethod() as it's a private method
                for (Method m : WebView.class.getDeclaredMethods()) {
                    if (m.getName().equals("setFindIsUp")) {
                        m.setAccessible(true);
                        m.invoke(wvBrowser, true);
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void downloadPage(boolean bReplaceStyle) {
        wvBrowser.clearCache(true);
        restoreStyle();
        loader = new LoaderTask(this);
        status.setCrash(false);
        status.setLoad(true);
        if (bReplaceStyle)
            loader.execute(link, DataBase.LINK, "");
        else
            loader.execute(link, DataBase.LINK);
    }

    private void initViews() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setVisibility(View.GONE);
        wvBrowser = (WebView) findViewById(R.id.wvBrowser);
        tip = new Tip(this, findViewById(R.id.tvFinish));
        pSearch = findViewById(R.id.pSearch);
        tvPromTime = (TextView) findViewById(R.id.tvPromTime);
        status = new StatusBar(this, findViewById(R.id.pStatus));
        fabMenu = findViewById(R.id.fabMenu);
        dbJournal = new DataBase(this, DataBase.JOURNAL);
        prom = new Prom(this);
        NavigationView navMenu = (NavigationView) findViewById(R.id.nav_view);
        navMenu.setNavigationItemSelectedListener(this);
//        miLight = navMenu.getMenu().getItem(4).getSubMenu().getItem(0);
//        miDark = navMenu.getMenu().getItem(4).getSubMenu().getItem(1);
        miNomenu = navMenu.getMenu().getItem(3);
        miTheme = navMenu.getMenu().getItem(4);
        drawerMenu = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerMenu, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerMenu.addDrawerListener(toggle);
        toggle.syncState();
        lib = new Lib(this);
        pref = getSharedPreferences(this.getLocalClassName(), MODE_PRIVATE);
        editor = pref.edit();
        bTheme = pref.getInt(THEME, 0) == 0;
        if (!bTheme) //dark
            findViewById(R.id.content_browser).setBackgroundColor(getResources().getColor(R.color.black));
        wvBrowser.getSettings().setBuiltInZoomControls(true);
        wvBrowser.getSettings().setDisplayZoomControls(false);
        int z = pref.getInt(SCALE, 0);
        if (z > 0)
            wvBrowser.setInitialScale(z);
        anMin = AnimationUtils.loadAnimation(this, R.anim.minimize);
        anMin.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                if (!bNomenu)
                    fabMenu.setVisibility(View.GONE);
                if (prom.isProm())
                    tvPromTime.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        anMax = AnimationUtils.loadAnimation(this, R.anim.maximize);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (iPlace > -1) {
            closeSearch();
        } else if (history.size() > 0) {
            boolBack = true;
            openLink(history.get(0));
            history.remove(0);
        } else {
            super.onBackPressed();
        }
    }

    private void setViews() {
        wvBrowser.setWebViewClient(new WebClient(this));
        wvBrowser.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    if (!bNomenu && iPlace == -1)
                        fabMenu.startAnimation(anMin);
                    if (prom.isProm())
                        tvPromTime.startAnimation(anMin);
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP ||
                        event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    if (!bNomenu && iPlace == -1) {
                        fabMenu.setVisibility(View.VISIBLE);
                        fabMenu.startAnimation(anMax);
                    }
                    if (prom.isProm()) {
                        tvPromTime.setVisibility(View.VISIBLE);
                        tvPromTime.startAnimation(anMax);
                    }
                }
                if (android.os.Build.VERSION.SDK_INT > 18) {
                    if (event.getPointerCount() == 2) {
                        bTwo = true;
                    } else if (bTwo) {
                        bTwo = false;
                        wvBrowser.setInitialScale((int) (wvBrowser.getScale() * 100.0));
                    }
                }
                return false;
            }
        });
        fabMenu.setOnClickListener(new View.OnClickListener() {
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
            fabMenu.setVisibility(View.GONE);
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        //http://stackoverflow.com/questions/35484767/activitycompat-requestpermissions-not-showing-dialog-box
        if (grantResults.length > 0 && grantResults[0] == 0) {
            if (requestCode == CODE_OPEN)
                openFile();
            else
                downloadFile(getFile());
        } else
            Lib.showToast(this, getResources().getString(R.string.permission_denied));
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_refresh) {
            if (loader == null) {
                if (link.contains(PNG)) {
                    if (lib.verifyStoragePermissions(CODE_DOWNLOAD))
                        return true;
                    downloadFile(getFile());
                } else
                    downloadPage(true);
            }
        } else if (id == R.id.nav_nomenu) {
            bNomenu = !bNomenu;
            setCheckItem(item, bNomenu);
            editor.putBoolean(NOMENU, bNomenu);
            if (bNomenu)
                fabMenu.setVisibility(View.GONE);
            else
                fabMenu.setVisibility(View.VISIBLE);
        } else if (id == R.id.nav_marker) {
            Intent marker = new Intent(getApplicationContext(), MarkerActivity.class);
            marker.putExtra(DataBase.TITLE, wvBrowser.getTitle());
            marker.putExtra(DataBase.LINK, link);
            marker.putExtra(DataBase.PLACE, (((float) wvBrowser.getScrollY()) / wvBrowser.getScale())
                    / ((float) wvBrowser.getContentHeight()) * 100.0f);
            startActivity(marker);
        } else if (id == R.id.nav_scale) {
            //tut
            wvBrowser.setInitialScale((int) (100f * getResources().getDisplayMetrics().density));
        } else if (id == R.id.nav_light || id == R.id.nav_dark) {
            if ((id == R.id.nav_light && bTheme)
                    || (id == R.id.nav_dark && !bTheme))
                return true;
            bTheme = !bTheme;
            setCheckItem(miTheme.getSubMenu().getItem(0), bTheme);
            setCheckItem(miTheme.getSubMenu().getItem(1), !bTheme);
            editor.putInt(THEME, (bTheme ? 0 : 1));
            wvBrowser.clearCache(true);
            openPage(false);
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void setCheckItem(MenuItem item, boolean check) {
        if (check) {
            item.setIcon(R.drawable.check_transparent);
        } else {
            item.setIcon(R.drawable.none);
        }
    }

    private void restoreStyle() {
        final File fStyle = lib.getFile(STYLE);
        if (fStyle.exists()) {
            final File fDark = lib.getFile(Lib.DARK);
            if (fDark.exists())
                fStyle.renameTo(lib.getFile(Lib.LIGHT));
            else
                fStyle.renameTo(fDark);
        }
    }

    public void openLink(String url) {
        if (!link.equals(url)) {
            if (!url.contains(PAGE)) {
                if (boolBack)
                    boolBack = false;
                else
                    history.add(0, link);
                link = url;
                dbPage = new DataBase(this, link);
            }
            miTheme.setVisible(!link.contains(PNG));
        }
        if (url.contains(PNG)) {
            openFile();
        } else {
            if (dbPage.existsPage(link))
                openPage(true);
            else
                downloadPage(false);
        }
    }

    private void openFile() {
        status.setLoad(false);
        if (lib.verifyStoragePermissions(CODE_OPEN)) return;
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

    public void openPage(boolean newPage) {
        status.setLoad(false);
        final File fLight = lib.getFile(Lib.LIGHT);
        final File fDark = lib.getFile(Lib.DARK);
        final File fStyle = lib.getFile(STYLE);
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

        try {
            File file = new File(getFilesDir() + PAGE);
            String s;
            if (newPage) {
                dbPage = new DataBase(this, link);
                if (!dbPage.existsPage(link)) {
                    downloadPage(false);
                    return;
                }
                BufferedWriter bw = new BufferedWriter(new FileWriter(file));

                SQLiteDatabase db = dbPage.getWritableDatabase();
                Cursor cursor = db.query(DataBase.TITLE, null,
                        DataBase.LINK + DataBase.Q, new String[]{link},
                        null, null, null);
                int id;
                Date d;
                if (cursor.moveToFirst()) {
                    id = cursor.getInt(cursor.getColumnIndex(DataBase.ID));
                    s = dbPage.getPageTitle(cursor.getString(cursor.getColumnIndex(DataBase.TITLE)), link);
                    d = new Date(cursor.getLong(cursor.getColumnIndex(DataBase.TIME)));
                    bw.write("<html><head>\n<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n");
                    bw.write("<link rel=\"stylesheet\" type=\"text/css\" href=\"");
                    bw.flush();
                    bw.write(STYLE.substring(1));
                    bw.write("\">\n</head><body>\n<h1 class=\"page-title\">");
                    bw.write(s);
                    bw.write("</h1>\n");
                    bw.flush();
                } else {
                    //заголовка нет - значит нет и страницы
                    //сюда никогдане попадет, т.к. выше есть проверка existsPage
                    return;
                }
                cursor.close();
                db = dbPage.getWritableDatabase();
                cursor = db.query(DataBase.PARAGRAPH, new String[]{DataBase.PARAGRAPH},
                        DataBase.ID + DataBase.Q, new String[]{String.valueOf(id)},
                        null, null, null);
                if (cursor.moveToFirst()) {
                    do {
                        bw.write(cursor.getString(0));
                        bw.write(Lib.N);
                        bw.flush();
                    } while (cursor.moveToNext());
                }
                cursor.close();
                dbPage.close();
                DateFormat df = new SimpleDateFormat("yy");
                bw.write("<div style=\"margin-top:20px\" class=\"print2\">\n");
                bw.write(getResources().getString(R.string.page) + " " + Lib.SITE + link);
                bw.write("<br>Copyright " + getResources().getString(R.string.copyright) + " Leonid Maslov 2004-20");
                bw.write(df.format(d) + "<br>");
                df = new SimpleDateFormat("HH:mm:ss dd.MM.yy");
                bw.write(getResources().getString(R.string.downloaded) + " " + df.format(d));
                bw.write("\n</div></body></html>");
                bw.close();
            }
            s = file.toString();
            if (link.contains("#"))
                s += link.substring(link.indexOf("#"));
            wvBrowser.loadUrl(FILE + s);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addJournal() {
        ContentValues cv = new ContentValues();
        cv.put(DataBase.TIME, System.currentTimeMillis());
        String id = dbPage.getDatePage(link) + "&" + dbPage.getPageId(link);
        cv.put(DataBase.ID, id);
        SQLiteDatabase db = dbJournal.getWritableDatabase();
        int i = db.update(DataBase.JOURNAL, cv, DataBase.ID + DataBase.Q, new String[]{id});
        if (i == 0) // no update
            db.insert(DataBase.JOURNAL, null, cv);
        Cursor cursor = db.query(DataBase.JOURNAL, null, null, null, null, null, null);
        if (cursor.getCount() > 100) {
            cursor.moveToFirst();
            i = cursor.getColumnIndex(DataBase.ID);
            db.delete(DataBase.JOURNAL, DataBase.ID + DataBase.Q, new String[]{cursor.getString(i)});
        }
        cursor.close();
        dbJournal.close();
    }

    public void openInApps(String url) {
        lib.openInApps(url, null);
    }

    public void finishLoad(boolean suc) {
        loader = null;
        if (suc) {
            status.setLoad(false);
            if (link.contains(PNG))
                openFile();
            else
                openPage(true);
        } else {
            status.setCrash(true);
        }
    }
}
