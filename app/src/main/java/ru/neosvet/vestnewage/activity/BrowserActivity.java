package ru.neosvet.vestnewage.activity;

import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
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
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.Data;

import com.google.android.material.navigation.NavigationView;

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
import ru.neosvet.vestnewage.fragment.BookFragment;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.helpers.PromHelper;
import ru.neosvet.vestnewage.helpers.UnreadHelper;
import ru.neosvet.vestnewage.model.LoaderModel;

public class BrowserActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, Observer<Data> {
    public static final String THEME = "theme", NOMENU = "nomenu",
            NAVBUTTONS = "navb", SCALE = "scale", FILE = "file://",
            STYLE = "/style/style.css", PAGE = "/page.html";
    private final String script = "<a href='javascript:NeoInterface.";
    private List<String> history = new ArrayList<String>();
    private boolean nomenu, navbuttons, lightTheme, twoPointers = false, back = false;
    private SharedPreferences pref;
    private SharedPreferences.Editor editor;
    private SoftKeyboard softKeyboard;
    private LoaderModel model;
    private WebView wvBrowser;
    private DataBase dbPage;
    private EditText etSearch;
    private StatusButton status;
    private LinearLayout mainLayout;
    private View fabMenu, fabTop, fabBottom, tvPromTime, pSearch, bPrev, bNext, bBack;
    private DrawerLayout drawerMenu;
    private Lib lib;
    private String link = Const.LINK, string = null;
    private String[] place;
    private int iPlace = -1;
    private boolean didSearch = false;
    private PromHelper prom;
    private Animation anMin, anMax;
    private MenuItem miThemeL, miThemeD, miNomenu, miButtons, miRefresh, miShare;
    private Tip tip;
    private Runnable runBrowser = null, runNextPage = null, runPrevPage = null;


    public static void openReader(Context context, String link, @Nullable String search) {
        Intent intent = new Intent(context, BrowserActivity.class);
        intent.putExtra(Const.LINK, link);
        if (search != null)
            intent.putExtra(Const.SEARCH, search);
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
        initModel();
        restoreState(savedInstanceState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        prom.stop();
        ProgressHelper.removeObservers(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        prom.resume();
        ProgressHelper.addObserver(this, this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbPage.close();
    }

    private void initModel() {
        model = new ViewModelProvider(this).get(LoaderModel.class);
        if (LoaderModel.inProgress)
            status.setLoad(true);
    }

    @Override
    public void onChanged(@Nullable Data data) {
        if (data.getBoolean(Const.START, false)) {
            status.loadText();
            return;
        }
        if (data.getBoolean(Const.FINISH, false)) {
            String error = data.getString(Const.ERROR);
            if (error != null) {
                status.setError(error);
                return;
            }
            status.setLoad(false);
            status.checkTime(DateHelper.initNow(this).getTimeInSeconds());
            openPage(true);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(Const.LINK, link);
        outState.putFloat(DataBase.PARAGRAPH, getPositionOnPage());
        outState.putString(Const.SEARCH, string);
        if (iPlace > -1) {
            outState.putInt(Const.PLACE, iPlace);
            outState.putStringArray(Const.STRING, place);
        }
        super.onSaveInstanceState(outState);
    }

    private void restoreState(Bundle state) {
        if (state == null) {
            openLink(getIntent().getStringExtra(Const.LINK), true);
            string = getIntent().getStringExtra(Const.SEARCH);
            if (string != null && string.contains(Const.NN)) {
                place = string.split(Const.NN);
                iPlace = 0;
                string = place[iPlace];
            }
        } else {
            link = state.getString(Const.LINK);
            if (link == null) return;
            dbPage = new DataBase(this, link);
            if (!LoaderModel.inProgress) {
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
            iPlace = state.getInt(Const.PLACE, -1);
            if (iPlace > -1) {
                place = state.getStringArray(Const.STRING);
                string = place[iPlace];
            } else
                string = state.getString(Const.SEARCH);
        }
        if (string != null) {
            etSearch.setText(string);
            if (iPlace > -1)
                etSearch.setEnabled(false);
            // findText(string);
            pSearch.setVisibility(View.VISIBLE);
            fabMenu.setVisibility(View.GONE);
        }
    }

    private void closeSearch() {
        tip.hide();
        string = null;
        if (iPlace > -1) {
            iPlace = -1;
            place = null;
            etSearch.setText("");
            etSearch.setEnabled(true);
        }
        didSearch = false;
        pSearch.setVisibility(View.GONE);
        if (!nomenu) {
            fabMenu.setVisibility(View.VISIBLE);
            fabMenu.startAnimation(anMax);
        }
        wvBrowser.clearMatches();
    }

    private void findText(String s) {
        if (s.contains(Const.N))
            s = s.substring(0, s.indexOf(Const.N));
        if (android.os.Build.VERSION.SDK_INT > 15)
            wvBrowser.findAllAsync(s);
        else {
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
        didSearch = true;
    }

    private void downloadPage(boolean replaceStyle) {
        wvBrowser.clearCache(true);
        restoreStyle();
        status.setLoad(true);
        status.startText();
        model.startLoad(replaceStyle, link);
    }

    private void initViews() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setVisibility(View.GONE);
        InputMethodManager im = (InputMethodManager) getSystemService(Service.INPUT_METHOD_SERVICE);
        mainLayout = findViewById(R.id.content_browser);
        softKeyboard = new SoftKeyboard(mainLayout, im);
        NavigationView navMenu = (NavigationView) findViewById(R.id.nav_view);
        navMenu.setNavigationItemSelectedListener(this);
        miRefresh = navMenu.getMenu().getItem(0);
        miShare = navMenu.getMenu().getItem(1);
        miNomenu = navMenu.getMenu().getItem(6);
        miButtons = navMenu.getMenu().getItem(7);
        miThemeL = navMenu.getMenu().getItem(8);
        miThemeD = navMenu.getMenu().getItem(9);

        wvBrowser = (WebView) findViewById(R.id.wvBrowser);
        tip = new Tip(this, findViewById(R.id.tvFinish));
        pSearch = findViewById(R.id.pSearch);
        etSearch = findViewById(R.id.etSearch);
        bPrev = findViewById(R.id.bPrev);
        bNext = findViewById(R.id.bNext);
        bBack = navMenu.getHeaderView(0).findViewById(R.id.bBack);
        status = new StatusButton(this, findViewById(R.id.pStatus));
        fabMenu = findViewById(R.id.fabMenu);
        fabTop = findViewById(R.id.fabTop);
        fabBottom = findViewById(R.id.fabBottom);

        SharedPreferences prMain = getSharedPreferences(MainActivity.class.getSimpleName(), MODE_PRIVATE);
        if (prMain.getBoolean(Const.COUNT_IN_MENU, true))
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
        pref = getSharedPreferences(BrowserActivity.class.getSimpleName(), MODE_PRIVATE);
        editor = pref.edit();
        lightTheme = pref.getInt(THEME, 0) == 0;
        if (lightTheme) {
            etSearch.setTextColor(getResources().getColor(android.R.color.black));
            etSearch.setHintTextColor(getResources().getColor(R.color.dark_gray));
        } else
            mainLayout.setBackgroundColor(getResources().getColor(android.R.color.black));
        etSearch.requestLayout();
        mainLayout.requestLayout();
        wvBrowser.getSettings().setBuiltInZoomControls(true);
        wvBrowser.getSettings().setDisplayZoomControls(false);
        wvBrowser.getSettings().setJavaScriptEnabled(true);
        wvBrowser.addJavascriptInterface(this, "NeoInterface");
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
        openLink(history.get(0), false);
        history.remove(0);
    }

    private void setViews() {
        wvBrowser.setWebViewClient(new WebClient(this));
        if (android.os.Build.VERSION.SDK_INT > 22)
            wvBrowser.setOnScrollChangeListener(new View.OnScrollChangeListener() {
                @Override
                public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                    if (!navbuttons)
                        return;
                    if (scrollY > 300) {
                        fabTop.setVisibility(View.VISIBLE);
                        fabBottom.setVisibility(View.GONE);
                    } else {
                        fabTop.setVisibility(View.GONE);
                        fabBottom.setVisibility(View.VISIBLE);
                    }
                }
            });
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
        fabTop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                wvBrowser.scrollTo(0, 0);
            }
        });
        fabBottom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                wvBrowser.scrollTo(0, (int) (wvBrowser.getContentHeight()
                        * wvBrowser.getScale()));
            }
        });
        etSearch.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
                if ((keyEvent.getAction() == KeyEvent.ACTION_DOWN && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER)
                        || keyCode == EditorInfo.IME_ACTION_SEARCH) {
                    if (etSearch.length() > 0) {
                        searchOk();
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
                if (iPlace > -1) {
                    if (--iPlace == -1)
                        iPlace = place.length - 1;
                    etSearch.setText(place[iPlace]);
                    findText(place[iPlace]);
                    return;
                }
                searchOk();
                if (!didSearch)
                    initSearch();
                wvBrowser.findNext(false);
            }
        });
        bNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (iPlace > -1) {
                    if (++iPlace == place.length)
                        iPlace = 0;
                    etSearch.setText(place[iPlace]);
                    findText(place[iPlace]);
                    return;
                }
                searchOk();
                if (!didSearch)
                    initSearch();
                wvBrowser.findNext(true);
            }
        });
        findViewById(R.id.bClose).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                softKeyboard.closeSoftKeyboard();
                closeSearch();
            }
        });
        bBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BrowserActivity.this.finish();
            }
        });
        initTheme();
        if (pref.getBoolean(NOMENU, false)) {
            nomenu = true;
            setCheckItem(miNomenu, nomenu);
            fabMenu.setVisibility(View.GONE);
        }
        if (pref.getBoolean(NAVBUTTONS, true)) {
            navbuttons = true;
            setCheckItem(miButtons, navbuttons);
            if (android.os.Build.VERSION.SDK_INT < 23) {
                fabTop.setVisibility(View.VISIBLE);
                fabBottom.setVisibility(View.GONE);
            }
        } else
            fabBottom.setVisibility(View.GONE);
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

    private void searchOk() {
        string = etSearch.getText().toString();
        softKeyboard.closeSoftKeyboard();
    }

    private void initTheme() {
        if (lightTheme) {
            etSearch.setTextColor(getResources().getColor(android.R.color.black));
            etSearch.setHintTextColor(getResources().getColor(R.color.dark_gray));
            mainLayout.setBackgroundColor(getResources().getColor(android.R.color.white));
            setCheckItem(miThemeL, true);
        } else {
            etSearch.setTextColor(getResources().getColor(android.R.color.white));
            etSearch.setHintTextColor(getResources().getColor(R.color.light_gray));
            mainLayout.setBackgroundColor(getResources().getColor(android.R.color.black));
            setCheckItem(miThemeD, true);
        }
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
        switch (id) {
            case R.id.nav_refresh:
                downloadPage(true);
                break;
            case R.id.nav_share:
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                shareIntent.setType("text/plain");
                String s = wvBrowser.getTitle();
                if (s.length() > 9)
                    s = s.substring(9) + " (" + getResources().getString(R.string.from) + " " + s.substring(0, 8) + ")";
                shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, s + Const.N + Const.SITE + link);
                startActivity(Intent.createChooser(shareIntent, getResources().getString(R.string.share)));
                break;
            case R.id.nav_nomenu:
                nomenu = !nomenu;
                setCheckItem(item, nomenu);
                editor.putBoolean(NOMENU, nomenu);
                if (nomenu)
                    fabMenu.setVisibility(View.GONE);
                else
                    fabMenu.setVisibility(View.VISIBLE);
                break;
            case R.id.nav_buttons:
                navbuttons = !navbuttons;
                setCheckItem(item, navbuttons);
                editor.putBoolean(NAVBUTTONS, navbuttons);
                if (navbuttons) {
                    fabTop.setVisibility(View.VISIBLE);
                } else {
                    fabTop.setVisibility(View.GONE);
                    fabBottom.setVisibility(View.GONE);
                }
                break;
            case R.id.nav_search:
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
                break;
            case R.id.nav_marker:
                Intent marker = new Intent(getApplicationContext(), MarkerActivity.class);
                marker.putExtra(Const.LINK, link);
                marker.putExtra(Const.PLACE, getPositionOnPage() * 100f);
                if (string != null)
                    marker.putExtra(Const.DESCTRIPTION, getResources().getString(R.string.search_for)
                            + " “" + string + "”");
                startActivity(marker);
                break;
            case R.id.nav_opt_scale:
            case R.id.nav_src_scale:
                editor.putInt(SCALE, id == R.id.nav_opt_scale ? 0 : 100);
                editor.apply();
                openReader(BrowserActivity.this, link, null);
                finish();
                return true;
            default:
                if ((id == R.id.nav_light && lightTheme) || (id == R.id.nav_dark && !lightTheme))
                    return true;
                lightTheme = !lightTheme;
                setCheckItem(miThemeL, lightTheme);
                setCheckItem(miThemeD, !lightTheme);
                mainLayout.setBackgroundColor(getResources().getColor(
                        lightTheme ? android.R.color.white : android.R.color.black));
                editor.putInt(THEME, (lightTheme ? 0 : 1));
                wvBrowser.clearCache(true);
                openPage(false);
                initTheme();
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

    public void openLink(String url, boolean add_history) {
        if (url == null) return;
        if (!url.contains(Const.HTML) && !url.contains("http:")) {
            lib.openInApps(url, null);
            return;
        }
        if (!link.equals(url)) {
            if (!url.contains(PAGE)) {
                if (back)
                    back = false;
                else if (!link.equals(Const.LINK) && add_history) //first value
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

    public void openPage(boolean newPage) {
        status.setLoad(false);
        if (link == null) return;
        if (!readyStyle())
            return;
        try {
            File file = new File(getFilesDir() + PAGE);
            if (newPage || !file.exists())
                generatePage(file);
            String s = file.toString();
            if (link.contains("#"))
                s += link.substring(link.indexOf("#"));
            wvBrowser.loadUrl(FILE + s);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void generatePage(File file) throws IOException {
        BufferedWriter bw = new BufferedWriter(new FileWriter(file));
        dbPage = new DataBase(this, link);
        Cursor cursor = dbPage.query(Const.TITLE, null, Const.LINK + DataBase.Q, link);
        int id;
        DateHelper d;
        if (cursor.moveToFirst()) {
            id = cursor.getInt(cursor.getColumnIndex(DataBase.ID));
            String s = dbPage.getPageTitle(cursor.getString(cursor.getColumnIndex(Const.TITLE)), link);
            d = DateHelper.putMills(this, cursor.getLong(cursor.getColumnIndex(Const.TIME)));
            if (dbPage.isArticle()) //раз в неделю предлагать обновить статьи
                status.checkTime(d.getTimeInSeconds());
            bw.write("<html><head>\n<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n");
            bw.write("<title>");
            bw.write(s);
            bw.write("</title>\n");
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
        cursor = dbPage.query(DataBase.PARAGRAPH, new String[]{DataBase.PARAGRAPH}, DataBase.ID + DataBase.Q, id);
        boolean poems = link.contains("poems/");
        if (cursor.moveToFirst()) {
            do {
                if (poems) {
                    bw.write("<p class='poem'");
                    bw.write(cursor.getString(0).substring(2));
                } else
                    bw.write(cursor.getString(0));
                bw.write(Const.N);
                bw.flush();
            } while (cursor.moveToNext());
        }
        cursor.close();
        bw.write("<div style=\"margin-top:20px\" class=\"print2\">\n");
        if (dbPage.isBook()) {
            bw.write(script);
            bw.write("PrevPage();'>На предыдущую</a> | ");
            bw.write(script);
            bw.write("NextPage();'>На следующую</a>");
            bw.write(Const.BR);
        }
        if (link.contains("print")) {// материалы с сайта Откровений
            miRefresh.setVisible(false);
            miShare.setVisible(false);
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

    private boolean readyStyle() {
        final File fLight = lib.getFile(Const.LIGHT);
        final File fDark = lib.getFile(Const.DARK);
        if (!fLight.exists() && !fDark.exists()) { //download style
            status.setLoad(true);
            model.startLoad(true, null);
            return false;
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
        return true;
    }

    public void checkUnread() {
        UnreadHelper unread = new UnreadHelper(BrowserActivity.this);
        unread.deleteLink(link);
    }

    public void addJournal() {
        new Thread(this::runJournal).start();
    }

    public void openInApps(String url) {
        lib.openInApps(url, null);
    }

    private float getPositionOnPage() {
        return (((float) wvBrowser.getScrollY()) / wvBrowser.getScale())
                / ((float) wvBrowser.getContentHeight());
    }

    @JavascriptInterface
    public void NextPage() { // NeoInterface
        if (runBrowser != null)
            wvBrowser.removeCallbacks(runBrowser);
        if (runNextPage == null)
            runNextPage = new Runnable() {
                @Override
                public void run() {
                    try {
                        String s = dbPage.getNextPage(link);
                        if (s != null) {
                            openLink(s, false);
                            return;
                        }
                        final String today = DateHelper.initToday(BrowserActivity.this).getMY();
                        DateHelper d = getDateFromLink();
                        if (d.getMY().equals(today)) {
                            tipEndList();
                            return;
                        }
                        d.changeMonth(1);
                        dbPage.close();
                        dbPage = new DataBase(BrowserActivity.this, d.getMY());
                        Cursor cursor = dbPage.getCursor(link.contains(Const.POEMS));
                        if (cursor.moveToFirst()) {
                            openLink(cursor.getString(0), false);
                            return;
                        }
                    } catch (Exception e) {
                    }
                    tipEndList();
                }
            };
        runBrowser = runNextPage;
        wvBrowser.post(runBrowser);
    }

    private DateHelper getDateFromLink() throws Exception {
        String s = link.substring(link.lastIndexOf("/") + 1, link.lastIndexOf("."));
        if (s.contains("_"))
            s = s.substring(0, s.indexOf("_"));
        return DateHelper.parse(this, s);
    }

    @JavascriptInterface
    public void PrevPage() { // NeoInterface
        if (runBrowser != null)
            wvBrowser.removeCallbacks(runBrowser);
        if (runPrevPage == null)
            runPrevPage = new Runnable() {
                @Override
                public void run() {
                    try {
                        String s = dbPage.getPrevPage(link);
                        if (s != null) {
                            openLink(s, false);
                            return;
                        }
                        final String min = getMinMY();
                        DateHelper d = getDateFromLink();
                        if (d.getMY().equals(min)) {
                            tipEndList();
                            return;
                        }
                        d.changeMonth(-1);
                        dbPage.close();
                        dbPage = new DataBase(BrowserActivity.this, d.getMY());
                        Cursor cursor = dbPage.getCursor(link.contains(Const.POEMS));
                        if (cursor.moveToLast()) {
                            openLink(cursor.getString(0), false);
                            return;
                        }
                    } catch (Exception e) {
                        tipEndList();
                        return;
                    }
                    tipEndList();
                }
            };
        runBrowser = runPrevPage;
        wvBrowser.post(runBrowser);
    }

    private void tipEndList() {
        Lib.showToast(this, getResources().getString(R.string.tip_end_list));
    }

    private String getMinMY() {
        DateHelper d;
        if (link.contains(Const.POEMS)) {
            d = DateHelper.putYearMonth(this, 2016, 2);
            return d.getMY();
        }
        SharedPreferences pref = getSharedPreferences(BookFragment.class.getSimpleName(), Context.MODE_PRIVATE);
        if (pref.getBoolean(Const.OTKR, false))
            d = DateHelper.putYearMonth(this, 2004, 8);
        else
            d = DateHelper.putYearMonth(this, 2016, 1);
        return d.getMY();
    }


    public void runJournal() {
        ContentValues cv = new ContentValues();
        cv.put(Const.TIME, System.currentTimeMillis());
        String id = dbPage.getDatePage(link) + Const.AND + dbPage.getPageId(link);
        DataBase dbJournal = new DataBase(BrowserActivity.this, DataBase.JOURNAL);
        try {
            int i = dbJournal.update(DataBase.JOURNAL, cv, DataBase.ID + DataBase.Q, id);
            if (i == 0) {// no update
                cv.put(DataBase.ID, id);
                dbJournal.insert(DataBase.JOURNAL, cv);
            }
            Cursor cursor = dbJournal.query(DataBase.JOURNAL, new String[]{DataBase.ID});
            i = cursor.getCount();
            cursor.moveToFirst();
            while (i > 100) {
                dbJournal.delete(DataBase.JOURNAL, DataBase.ID + DataBase.Q, cursor.getString(0));
                cursor.moveToNext();
                i--;
            }
            cursor.close();
            dbJournal.close();
        } catch (Exception e) {
            dbJournal.close();
            File file = new File(getFilesDir().getParent() + "/databases/" + DataBase.JOURNAL);
            file.delete();
                    /*db.execSQL("drop table if exists " + DataBase.JOURNAL); // удаляем таблицу старого образца
                    //создаем таблицу нового образца:
                    db.execSQL("create table " + DataBase.JOURNAL + " ("
                            + DataBase.ID + " text primary key,"
                            + Const.TIME + " integer);");*/
        }
    }

    public void initSearch() {
        if (string != null)
            findText(string);
    }
}
