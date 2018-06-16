package ru.neosvet.vestnewage.activity;

import android.app.Activity;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import ru.neosvet.ui.SoftKeyboard;
import ru.neosvet.ui.StatusButton;
import ru.neosvet.ui.Tip;
import ru.neosvet.ui.WebClient;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.PromHelper;
import ru.neosvet.vestnewage.helpers.UnreadHelper;
import ru.neosvet.vestnewage.task.LoaderTask;

public class BrowserActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {
    public static final String THEME = "theme", NOMENU = "nomenu",
            SCALE = "scale", FILE = "file://", PNG = ".png",
            STYLE = "/style/style.css", PAGE = "/page.html";
    private final int CODE_OPEN = 1;
    private List<String> history = new ArrayList<String>();
    private boolean nomenu, lightTheme, twoPointers = false, back = false;
    private SharedPreferences pref;
    private SharedPreferences.Editor editor;
    private SoftKeyboard softKeyboard;
    private LoaderTask loader = null;
    private WebView wvBrowser;
    private DataBase dbPage, dbJournal;
    private TextView tvPlace;
    private EditText etSearch;
    private StatusButton status;
    private View fabMenu, tvPromTime, pSearch, bPrev, bNext;
    private DrawerLayout drawerMenu;
    private Lib lib;
    private String link = DataBase.LINK, string = null;
    private String[] place;
    private int iPlace = -1;
    private PromHelper prom;
    private Animation anMin, anMax;
    private MenuItem miTheme, miNomenu, miRefresh;
    private Tip tip;


    public static void openReader(Context context, String link, @Nullable String place) {
        Intent intent = new Intent(context, BrowserActivity.class);
        intent.putExtra(DataBase.LINK, link);
        if (place != null)
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
        outState.putSerializable(Const.TASK, loader);
        if (link.contains(PNG))
            outState.putString(DataBase.LINK, history.get(0));
        else
            outState.putString(DataBase.LINK, link);
        outState.putInt(DataBase.PLACE, iPlace);
        outState.putFloat(DataBase.PARAGRAPH, getPositionOnPage());
        outState.putString(DataBase.SEARCH, string);
        super.onSaveInstanceState(outState);
    }

    private void restoreActivityState(Bundle state) {
        if (state == null) {
            openLink(getIntent().getStringExtra(DataBase.LINK));
            if (getIntent().hasExtra(DataBase.PLACE))
                iPlace = 0;
        } else {
            loader = (LoaderTask) state.getSerializable(Const.TASK);
            link = state.getString(DataBase.LINK);
            dbPage = new DataBase(this, link);
            if (loader != null && loader.getStatus() == AsyncTask.Status.RUNNING) {
                if (loader.getStatus() == AsyncTask.Status.RUNNING) {
                    loader.setAct(this);
                    status.setLoad(true);
                } else loader = null;
            } else {
                openPage(false);
                final float pos = state.getFloat(DataBase.PARAGRAPH);
                final Handler h = new Handler() {
                    public void handleMessage(android.os.Message msg) {
                        wvBrowser.scrollTo(0, (int) (pos * wvBrowser.getScale()
                                * (float) wvBrowser.getContentHeight()));
                    }
                };
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            Thread.sleep(500);
                            h.sendEmptyMessage(0);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
            iPlace = state.getInt(DataBase.PLACE);
            string = state.getString(DataBase.SEARCH);
            if (string != null) {
                etSearch.setText(string);
                findText(string);
                pSearch.setVisibility(View.VISIBLE);
                fabMenu.setVisibility(View.GONE);
            }
        }
        miTheme.setVisible(!link.contains(PNG));
    }

    private void initPlace() {
        if (iPlace == -1) return;
        String p = getIntent().getStringExtra(DataBase.PLACE);
        fabMenu.setVisibility(View.GONE);
        tvPlace = (TextView) findViewById(R.id.tvPlace);
        tvPlace.setVisibility(View.VISIBLE);
        etSearch.setVisibility(View.GONE);
        if (p.contains(Const.NN)) {
            place = p.split(Const.NN);
        } else {
            place = new String[]{p};
            bPrev.setVisibility(View.GONE);
            bNext.setVisibility(View.GONE);
        }
        pSearch.setVisibility(View.VISIBLE);
    }

    private void closeSearch() {
        tip.hide();
        if (tvPlace != null) {
            bPrev.setVisibility(View.VISIBLE);
            bNext.setVisibility(View.VISIBLE);
            tvPlace.setVisibility(View.GONE);
            tvPlace.setText("");
            tvPlace = null;
            etSearch.setVisibility(View.VISIBLE);
            iPlace = -1;
            place = null;
        } else
            string = null;
        pSearch.setVisibility(View.GONE);
        if (!nomenu) {
            fabMenu.setVisibility(View.VISIBLE);
            fabMenu.startAnimation(anMax);
        }
        wvBrowser.clearMatches();
    }

    public void setPlace() {
        if (iPlace == -1) return;
        String s = place[iPlace];
        tvPlace.setText(s.replace(Const.N, " "));
        findText(s);
    }

    private void findText(String s) {
        if (android.os.Build.VERSION.SDK_INT > 15)
            wvBrowser.findAllAsync(s);
        else {
            if (s.contains(Const.N))
                s = s.substring(0, s.indexOf(Const.N));
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

    private void downloadPage(boolean replaceStyle) {
        wvBrowser.clearCache(true);
        restoreStyle();
        loader = new LoaderTask(this);
        status.setCrash(false);
        status.setLoad(true);
        if (replaceStyle)
            loader.execute(LoaderTask.DOWNLOAD_PAGE_WITH_STYLE, link);
        else
            loader.execute(LoaderTask.DOWNLOAD_PAGE, link);
    }

    private void initViews() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setVisibility(View.GONE);
        InputMethodManager im = (InputMethodManager) getSystemService(Service.INPUT_METHOD_SERVICE);
        LinearLayout mainLayout = (LinearLayout) findViewById(R.id.content_browser);
        softKeyboard = new SoftKeyboard(mainLayout, im);
        NavigationView navMenu = (NavigationView) findViewById(R.id.nav_view);
        navMenu.setNavigationItemSelectedListener(this);
        miRefresh = navMenu.getMenu().getItem(0);
        miNomenu = navMenu.getMenu().getItem(4);
        miTheme = navMenu.getMenu().getItem(5);

        wvBrowser = (WebView) findViewById(R.id.wvBrowser);
        tip = new Tip(this, findViewById(R.id.tvFinish));
        pSearch = findViewById(R.id.pSearch);
        etSearch = (EditText) findViewById(R.id.etSearch);
        bPrev = findViewById(R.id.bPrev);
        bNext = findViewById(R.id.bNext);
        status = new StatusButton(this, findViewById(R.id.pStatus));
        fabMenu = findViewById(R.id.fabMenu);
        dbJournal = new DataBase(this, DataBase.JOURNAL);

        String main = MainActivity.class.getName();
        main = main.substring(main.indexOf("act"));
        SharedPreferences prMain = getSharedPreferences(main, MODE_PRIVATE);
        if (prMain.getBoolean(MainActivity.COUNT_IN_MENU, true))
            prom = new PromHelper(this, navMenu.getHeaderView(0)
                    .findViewById(R.id.tvPromTimeInMenu));
        else {
            tvPromTime = findViewById(R.id.tvPromTime);
            prom = new PromHelper(this, tvPromTime);
        }

        drawerMenu = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerMenu, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerMenu.addDrawerListener(toggle);
        toggle.syncState();
        lib = new Lib(this);
        pref = getSharedPreferences(this.getLocalClassName(), MODE_PRIVATE);
        editor = pref.edit();
        lightTheme = pref.getInt(THEME, 0) == 0;
        if (!lightTheme) //dark
            findViewById(R.id.content_browser).setBackgroundColor(getResources().getColor(android.R.color.black));
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
                if (!nomenu)
                    fabMenu.setVisibility(View.GONE);
                if (prom.isProm() && tvPromTime != null)
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
        } else if (pSearch.getVisibility() == View.VISIBLE) {
            closeSearch();
        } else if (history.size() > 0) {
            onBackBrowser();
        } else {
            super.onBackPressed();
        }
    }

    private void onBackBrowser() {
        back = true;
        openLink(history.get(0));
        history.remove(0);
    }

    private void setViews() {
        wvBrowser.setWebViewClient(new WebClient(this));
        wvBrowser.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    if (!nomenu && pSearch.getVisibility() == View.GONE)
                        fabMenu.startAnimation(anMin);
                    if (prom.isProm() && tvPromTime != null)
                        tvPromTime.startAnimation(anMin);
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP ||
                        event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    if (!nomenu && pSearch.getVisibility() == View.GONE) {
                        fabMenu.setVisibility(View.VISIBLE);
                        fabMenu.startAnimation(anMax);
                    }
                    if (prom.isProm() && tvPromTime != null) {
                        tvPromTime.setVisibility(View.VISIBLE);
                        tvPromTime.startAnimation(anMax);
                    }
                }
                if (android.os.Build.VERSION.SDK_INT > 18) {
                    if (event.getPointerCount() == 2) {
                        twoPointers = true;
                    } else if (twoPointers) {
                        twoPointers = false;
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
        etSearch.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
                if ((keyEvent.getAction() == KeyEvent.ACTION_DOWN && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER)
                        || keyCode == EditorInfo.IME_ACTION_SEARCH) {
                    if (etSearch.length() > 0) {
                        string = etSearch.getText().toString();
                        softKeyboard.closeSoftKeyboard();
                        findText(string);
                    }
                    return true;
                }
                return false;
            }
        });
        bPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (tvPlace == null) {
                    softKeyboard.closeSoftKeyboard();
                    wvBrowser.findNext(false);
                } else if (iPlace > 0) {
                    iPlace--;
                    setPlace();
                } else
                    tip.show();
            }
        });
        bNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (tvPlace == null) {
                    softKeyboard.closeSoftKeyboard();
                    wvBrowser.findNext(true);
                } else if (iPlace < place.length - 1) {
                    iPlace++;
                    setPlace();
                } else
                    tip.show();
            }
        });
        findViewById(R.id.bClose).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                softKeyboard.closeSoftKeyboard();
                closeSearch();
            }
        });
        if (lightTheme)
            setCheckItem(miTheme.getSubMenu().getItem(0), true);
        else
            setCheckItem(miTheme.getSubMenu().getItem(1), true);
        if (pref.getBoolean(NOMENU, false)) {
            nomenu = true;
            setCheckItem(miNomenu, nomenu);
            fabMenu.setVisibility(View.GONE);
        }
        status.setClick(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (status.isTime())
                    downloadPage(true);
                else
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
        } else {
            lib.openInApps(Const.SITE + link, null);
            onBackBrowser();
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_refresh) {
            if (loader == null)
                downloadPage(true);
        } else if (id == R.id.nav_nomenu) {
            nomenu = !nomenu;
            setCheckItem(item, nomenu);
            editor.putBoolean(NOMENU, nomenu);
            if (nomenu)
                fabMenu.setVisibility(View.GONE);
            else
                fabMenu.setVisibility(View.VISIBLE);
        } else if (id == R.id.nav_search) {
            if (pSearch.getVisibility() == View.VISIBLE)
                closeSearch();
            fabMenu.setVisibility(View.GONE);
            pSearch.setVisibility(View.VISIBLE);
            etSearch.post(new Runnable() {
                @Override
                public void run() {
                    etSearch.requestFocus();
                }
            });
            softKeyboard.openSoftKeyboard();
        } else if (id == R.id.nav_marker) {
            if (iPlace > -1)
                MarkerActivity.addMarker(this, link, place[iPlace], null);
            else {
                Intent marker = new Intent(getApplicationContext(), MarkerActivity.class);
                marker.putExtra(DataBase.LINK, link);
                marker.putExtra(DataBase.PLACE, getPositionOnPage() * 100f);
                startActivity(marker);
            }
        } else if (id == R.id.nav_scale) {
            wvBrowser.setInitialScale((int) (100f * getResources().getDisplayMetrics().density));
        } else if (id == R.id.nav_light || id == R.id.nav_dark) {
            if ((id == R.id.nav_light && lightTheme)
                    || (id == R.id.nav_dark && !lightTheme))
                return true;
            lightTheme = !lightTheme;
            setCheckItem(miTheme.getSubMenu().getItem(0), lightTheme);
            setCheckItem(miTheme.getSubMenu().getItem(1), !lightTheme);
            editor.putInt(THEME, (lightTheme ? 0 : 1));
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
            final File fDark = lib.getFile(Const.DARK);
            if (fDark.exists())
                fStyle.renameTo(lib.getFile(Const.LIGHT));
            else
                fStyle.renameTo(fDark);
        }
    }

    public void openLink(String url) {
        if (url.contains(PNG)) {
            history.add(0, link);
            link = url;
            openFile();
            return;
        }
        if (!link.equals(url)) {
            if (!url.contains(PAGE)) {
                if (back)
                    back = false;
                else if (!link.equals(DataBase.LINK)) //first value
                    history.add(0, link);
                link = url;
                dbPage = new DataBase(this, link);
            }
        }
        if (dbPage.existsPage(link))
            openPage(true);
        else
            downloadPage(false);
    }

    private void openFile() {
        status.setLoad(false);
        File f = getFile();
        if (f.exists()) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(android.net.Uri.fromFile(f), "image/png");
                startActivity(intent);
            } catch (Exception e) {
            }
            onBackBrowser();
        } else
            downloadFile(f);
    }

    private File getFile() {
        return new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS).getAbsolutePath()
                .toString() + link.substring(link.lastIndexOf("/")));
    }


    private void downloadFile(File f) {
        loader = new LoaderTask(this);
        status.setLoad(true);
        loader.execute(LoaderTask.DOWNLOAD_FILE, Const.SITE + link, f.toString());
    }

    public void openPage(boolean newPage) {
        status.setLoad(false);
        final File fLight = lib.getFile(Const.LIGHT);
        final File fDark = lib.getFile(Const.DARK);
        if (!fLight.exists() && !fDark.exists()) { //download style
            loader = new LoaderTask(this);
            status.setCrash(false);
            status.setLoad(true);
            loader.execute(LoaderTask.DOWNLOAD_PAGE_WITH_STYLE, "");
            return;
        }
        final File fStyle = lib.getFile(STYLE);
        boolean replace = true;
        if (fStyle.exists()) {
            replace = (fDark.exists() && !lightTheme) || (fLight.exists() && lightTheme);
            if (replace) {
                if (fDark.exists())
                    fStyle.renameTo(fLight);
                else
                    fStyle.renameTo(fDark);
            }
        }
        if (replace) {
            if (lightTheme)
                fLight.renameTo(fStyle);
            else
                fDark.renameTo(fStyle);
        }
        try {
            File file = new File(getFilesDir() + PAGE);
            String s;
            if (newPage) {
                BufferedWriter bw = new BufferedWriter(new FileWriter(file));
                dbPage = new DataBase(this, link);
                SQLiteDatabase db = dbPage.getWritableDatabase();
                Cursor cursor = db.query(DataBase.TITLE, null,
                        DataBase.LINK + DataBase.Q, new String[]{link},
                        null, null, null);
                int id;
                DateHelper.Builder builder = DateHelper.newBuilder(this);
                DateHelper d;
                if (cursor.moveToFirst()) {
                    id = cursor.getInt(cursor.getColumnIndex(DataBase.ID));
                    s = dbPage.getPageTitle(cursor.getString(cursor.getColumnIndex(DataBase.TITLE)), link);
                    d = builder.setMills(cursor.getLong(cursor.getColumnIndex(DataBase.TIME))).build();
                    if (dbPage.getName().equals("00.00")) //раз в месяц предлагать обновить статьи
                        status.checkTime(d.getTimeInSeconds() - DateHelper.MONTH_IN_SEC);
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
                    cursor.close();
                    return;
                }
                cursor.close();
                cursor = db.query(DataBase.PARAGRAPH, new String[]{DataBase.PARAGRAPH},
                        DataBase.ID + DataBase.Q, new String[]{String.valueOf(id)},
                        null, null, null);
                if (cursor.moveToFirst()) {
                    do {
                        bw.write(cursor.getString(0));
                        bw.write(Const.N);
                        bw.flush();
                    } while (cursor.moveToNext());
                }
                cursor.close();
                dbPage.close();
                bw.write("<div style=\"margin-top:20px\" class=\"print2\">\n");
                if (link.contains("print")) {// материалы с сайта Откровений
                    miRefresh.setVisible(false);
                    bw.write("Copyright ");
                    bw.write(getResources().getString(R.string.copyright));
                    bw.write(" Leonid Maslov 2004-");
                    bw.write(d.getYear() + Const.BR);
                } else {
                    bw.write(getResources().getString(R.string.page) + " " + Const.SITE + link);
                    bw.write("<br>Copyright ");
                    bw.write(getResources().getString(R.string.copyright));
                    bw.write(" Leonid Maslov 2004-");
                    bw.write(d.getYear() + Const.BR);
                    bw.write(getResources().getString(R.string.downloaded) + " " + d.toString());
                }
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

    public void checkUnread() {
        UnreadHelper unread = new UnreadHelper(BrowserActivity.this);
        unread.deleteLink(link);
    }

    public void addJournal() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                ContentValues cv = new ContentValues();
                cv.put(DataBase.TIME, System.currentTimeMillis());
                String id = dbPage.getDatePage(link) + Const.AND + dbPage.getPageId(link);
                SQLiteDatabase db = dbJournal.getWritableDatabase();
                try {
                    int i = db.update(DataBase.JOURNAL, cv, DataBase.ID + DataBase.Q, new String[]{id});
                    if (i == 0) {// no update
                        cv.put(DataBase.ID, id);
                        db.insert(DataBase.JOURNAL, null, cv);
                    }
                    Cursor cursor = db.query(DataBase.JOURNAL, new String[]{DataBase.ID}, null, null, null, null, null);
                    i = cursor.getCount();
                    cursor.moveToFirst();
                    while (i > 100) {
                        db.delete(DataBase.JOURNAL, DataBase.ID + DataBase.Q,
                                new String[]{cursor.getString(0)});
                        cursor.moveToNext();
                        i--;
                    }
                    cursor.close();
                } catch (Exception e) {
                    db.execSQL("drop table if exists " + DataBase.JOURNAL); // удаляем таблицу старого образца
                    //создаем таблицу нового образца:
                    db.execSQL("create table " + DataBase.JOURNAL + " ("
                            + DataBase.ID + " text primary key,"
                            + DataBase.TIME + " integer);");
                }
                dbJournal.close();
            }
        }).start();
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

    public float getPositionOnPage() {
        return (((float) wvBrowser.getScrollY()) / wvBrowser.getScale())
                / ((float) wvBrowser.getContentHeight());
    }
}
