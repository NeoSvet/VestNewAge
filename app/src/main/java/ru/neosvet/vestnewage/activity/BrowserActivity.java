package ru.neosvet.vestnewage.activity;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.util.Timer;
import java.util.TimerTask;

import ru.neosvet.ui.SoftKeyboard;
import ru.neosvet.ui.StatusButton;
import ru.neosvet.ui.Tip;
import ru.neosvet.ui.WebClient;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.App;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.helpers.BrowserHelper;
import ru.neosvet.vestnewage.helpers.PromHelper;
import ru.neosvet.vestnewage.helpers.UnreadHelper;
import ru.neosvet.vestnewage.presenter.BrowserPresenter;
import ru.neosvet.vestnewage.presenter.view.BrowserView;

public class BrowserActivity extends AppCompatActivity implements BrowserView,
        NavigationView.OnNavigationItemSelectedListener {
    private boolean twoPointers = false;
    private SoftKeyboard softKeyboard;
    private WebView wvBrowser;
    private EditText etSearch;
    private StatusButton status;
    private LinearLayout mainLayout;
    private View fabMenu, fabTop, fabBottom, tvPromTime, pSearch, bPrev, bNext, bBack;
    private DrawerLayout drawerMenu;
    private PromHelper prom;
    private Animation anMin, anMax;
    private MenuItem miThemeL, miThemeD, miNomenu, miButtons, miRefresh, miShare;
    private Tip tip;
    private BrowserPresenter presenter;
    private BrowserHelper helper = new BrowserHelper();

    public static void openReader(String link, @Nullable String search) {
        Intent intent = new Intent(App.context, BrowserActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(Const.LINK, link);
        if (search != null)
            intent.putExtra(Const.SEARCH, search);
        App.context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.browser_activity);
        presenter = new BrowserPresenter(this, this);
        initViews();
        setViews();
        restoreState(savedInstanceState);
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
    protected void onDestroy() {
        presenter.setZoom((int) (wvBrowser.getScale() * 100.0));
        presenter.onDestroy();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(Const.LINK, presenter.getLink());
        outState.putFloat(DataBase.PARAGRAPH, getPositionOnPage());
        outState.putString(Const.SEARCH, helper.getSearch());
        outState.putInt(Const.PROG, helper.getProg());
        outState.putInt(Const.PLACE, helper.getIndex());
        super.onSaveInstanceState(outState);
    }

    private void restoreState(Bundle state) {
        float pos = 0f;
        if (state == null) {
            presenter.openLink(getIntent().getStringExtra(Const.LINK), true);
            String s = getIntent().getStringExtra(Const.SEARCH);
            if (s != null && !s.isEmpty())
                helper.setSearchString(s);
        } else {
            String link = state.getString(Const.LINK);
            if (link == null) return;
            presenter.openLink(link, true);
            pos = state.getFloat(DataBase.PARAGRAPH, pos);
            String s = state.getString(Const.SEARCH);
            if (!s.isEmpty()) {
                helper.setSearchString(s);
                int i = state.getInt(Const.PLACE, -1);
                if (i > -1)
                    helper.setSearchIndex(i);
                findText(helper.getCurrentSearch());
                int p = state.getInt(Const.PROG, 0);
                helper.setProg(p);
                if (p > 0) {
                    for (i = 0; i < p; i++)
                        wvBrowser.findNext(true);
                } else {
                    for (i = 0; i > p; i--)
                        wvBrowser.findNext(false);
                }
            }
        }
        if (helper.isSearch()) {
            etSearch.setText(helper.getCurrentSearch());
            if (helper.getIndex() > -1)
                etSearch.setEnabled(false);
            pSearch.setVisibility(View.VISIBLE);
            fabMenu.setVisibility(View.GONE);
        }
        if (pos > 0f)
            restorePosition(pos);
    }

    private void restorePosition(float pos) {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                wvBrowser.post(() -> {
                    wvBrowser.scrollTo(0, (int) (pos * wvBrowser.getScale()
                            * (float) wvBrowser.getContentHeight()));
                });
            }
        }, 500);
    }

    private void closeSearch() {
        closeKeyboard();
        tip.hide();
        if (helper.getIndex() > -1) {
            etSearch.setText("");
            etSearch.setEnabled(true);
        }
        helper.clearSearch();
        pSearch.setVisibility(View.GONE);
        if (!presenter.isNoMenu()) {
            fabMenu.setVisibility(View.VISIBLE);
            fabMenu.startAnimation(anMax);
        }
        wvBrowser.clearMatches();
    }

    private void findText(String s) {
        if (s.contains(Const.N))
            s = s.substring(0, s.indexOf(Const.N));
        wvBrowser.findAllAsync(s);
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
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
            prom = new PromHelper(navMenu.getHeaderView(0)
                    .findViewById(R.id.tvPromTimeInMenu));
        else {
            tvPromTime = findViewById(R.id.tvPromTime);
            prom = new PromHelper(tvPromTime);
        }

        drawerMenu = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerMenu, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerMenu.addDrawerListener(toggle);
        toggle.syncState();
        if (presenter.isLightTheme()) {
            etSearch.setTextColor(ContextCompat.getColor(this, android.R.color.black));
            etSearch.setHintTextColor(ContextCompat.getColor(this, R.color.dark_gray));
        } else
            mainLayout.setBackgroundColor(ContextCompat.getColor(this, android.R.color.black));
        etSearch.requestLayout();
        mainLayout.requestLayout();
        wvBrowser.getSettings().setBuiltInZoomControls(true);
        wvBrowser.getSettings().setDisplayZoomControls(false);
        wvBrowser.getSettings().setJavaScriptEnabled(true);
        wvBrowser.getSettings().setAllowContentAccess(true);
        wvBrowser.getSettings().setAllowFileAccess(true);
        wvBrowser.addJavascriptInterface(this, "NeoInterface");
        int z = presenter.getZoom();
        if (z > 0)
            wvBrowser.setInitialScale(z);
        anMin = AnimationUtils.loadAnimation(this, R.anim.minimize);
        anMin.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                if (!presenter.isNoMenu())
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
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (pSearch.getVisibility() == View.VISIBLE) {
            closeSearch();
        } else if (!presenter.onBackBrowser()) {
            super.onBackPressed();
        }
    }

    private void setViews() {
        wvBrowser.setWebViewClient(new WebClient(this));
        wvBrowser.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (!presenter.isNavButtons())
                return;
            if (scrollY > 300) {
                fabTop.setVisibility(View.VISIBLE);
                fabBottom.setVisibility(View.GONE);
            } else {
                fabTop.setVisibility(View.GONE);
                fabBottom.setVisibility(View.VISIBLE);
            }
        });
        wvBrowser.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (!presenter.isNoMenu() && pSearch.getVisibility() == View.GONE)
                    fabMenu.startAnimation(anMin);
                if (prom.isProm() && tvPromTime != null)
                    tvPromTime.startAnimation(anMin);
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP ||
                    event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                if (!presenter.isNoMenu() && pSearch.getVisibility() == View.GONE) {
                    fabMenu.setVisibility(View.VISIBLE);
                    fabMenu.startAnimation(anMax);
                }
                if (prom.isProm() && tvPromTime != null) {
                    tvPromTime.setVisibility(View.VISIBLE);
                    tvPromTime.startAnimation(anMax);
                }
            }
            if (event.getPointerCount() == 2) {
                twoPointers = true;
            } else if (twoPointers) {
                twoPointers = false;
                wvBrowser.setInitialScale((int) (wvBrowser.getScale() * 100.0));
            }
            return false;
        });
        fabMenu.setOnClickListener(view ->
                drawerMenu.openDrawer(Gravity.LEFT)
        );
        fabTop.setOnClickListener(view ->
                wvBrowser.scrollTo(0, 0)
        );
        fabBottom.setOnClickListener(view ->
                wvBrowser.scrollTo(0, (int) (wvBrowser.getContentHeight()
                        * wvBrowser.getScale()))
        );
        etSearch.setOnKeyListener((view, keyCode, keyEvent) -> {
            if ((keyEvent.getAction() == KeyEvent.ACTION_DOWN && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER)
                    || keyCode == EditorInfo.IME_ACTION_SEARCH) {
                if (etSearch.length() > 0) {
                    closeKeyboard();
                    helper.setSearchString(etSearch.getText().toString());
                    findText(helper.getCurrentSearch());
                }
                return true;
            }
            return false;
        });
        bPrev.setOnClickListener(view -> {
            if (helper.prevSearch()) {
                etSearch.setText(helper.getCurrentSearch());
                findText(helper.getCurrentSearch());
                return;
            }
            closeKeyboard();
            if (!helper.isSearch())
                initSearch();
            helper.downProg();
            wvBrowser.findNext(false);
        });
        bNext.setOnClickListener(view -> {
            if (helper.nextSearch()) {
                etSearch.setText(helper.getCurrentSearch());
                findText(helper.getCurrentSearch());
                return;
            }
            closeKeyboard();
            if (!helper.isSearch())
                initSearch();
            helper.upProg();
            wvBrowser.findNext(true);
        });
        findViewById(R.id.bClose).setOnClickListener(view -> closeSearch());
        bBack.setOnClickListener(view -> BrowserActivity.this.finish());
        initTheme();
        if (presenter.isNoMenu()) {
            setCheckItem(miNomenu, true);
            fabMenu.setVisibility(View.GONE);
        }
        if (presenter.isNavButtons())
            setCheckItem(miButtons, true);
        else
            fabBottom.setVisibility(View.GONE);
        status.setClick(view -> {
            if (status.isTime())
                presenter.downloadPage(true);
            else
                status.onClick();
        });
    }

    private void closeKeyboard() {
        softKeyboard.closeSoftKeyboard();
    }

    private void initTheme() {
        if (presenter.isLightTheme()) {
            etSearch.setTextColor(ContextCompat.getColor(this, android.R.color.black));
            etSearch.setHintTextColor(ContextCompat.getColor(this, R.color.dark_gray));
            mainLayout.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white));
            setCheckItem(miThemeL, true);
        } else {
            etSearch.setTextColor(ContextCompat.getColor(this, android.R.color.white));
            etSearch.setHintTextColor(ContextCompat.getColor(this, R.color.light_gray));
            mainLayout.setBackgroundColor(ContextCompat.getColor(this, android.R.color.black));
            setCheckItem(miThemeD, true);
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.nav_refresh:
                presenter.downloadPage(true);
                break;
            case R.id.nav_share:
                presenter.sharePage(this, wvBrowser.getTitle());
                break;
            case R.id.nav_nomenu:
                boolean nomenu = !presenter.isNoMenu();
                setCheckItem(item, nomenu);
                presenter.setNoMenu(nomenu);
                if (nomenu)
                    fabMenu.setVisibility(View.GONE);
                else
                    fabMenu.setVisibility(View.VISIBLE);
                break;
            case R.id.nav_buttons:
                boolean navbuttons = !presenter.isNavButtons();
                setCheckItem(item, navbuttons);
                presenter.setNavButtons(navbuttons);
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
                etSearch.post(() -> etSearch.requestFocus());
                softKeyboard.openSoftKeyboard();
                break;
            case R.id.nav_marker:
                Intent marker = new Intent(getApplicationContext(), MarkerActivity.class);
                marker.putExtra(Const.LINK, presenter.getLink());
                marker.putExtra(Const.PLACE, getPositionOnPage() * 100f);
                if (helper.isSearch())
                    marker.putExtra(Const.DESCTRIPTION, getString(R.string.search_for)
                            + " “" + helper.getCurrentSearch() + "”");
                startActivity(marker);
                break;
            case R.id.nav_opt_scale:
            case R.id.nav_src_scale:
                presenter.setZoom(id == R.id.nav_opt_scale ? 0 : 100);
                openReader(presenter.getLink(), null);
                finish();
                return true;
            default:
                boolean lightTheme = presenter.isLightTheme();
                if ((id == R.id.nav_light && lightTheme) || (id == R.id.nav_dark && !lightTheme))
                    return true;
                lightTheme = !lightTheme;
                setCheckItem(miThemeL, lightTheme);
                setCheckItem(miThemeD, !lightTheme);
                mainLayout.setBackgroundColor(ContextCompat.getColor(this,
                        lightTheme ? android.R.color.white : android.R.color.black));
                presenter.setLightTheme(lightTheme);
                wvBrowser.clearCache(true);
                presenter.openPage(false);
                initTheme();
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
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

    public void onPageFinished() {
        UnreadHelper unread = new UnreadHelper();
        unread.deleteLink(presenter.getLink());
        presenter.addJournal();
    }

    private float getPositionOnPage() {
        return (((float) wvBrowser.getScrollY()) / wvBrowser.getScale())
                / ((float) wvBrowser.getContentHeight());
    }

    @JavascriptInterface
    public void NextPage() { // NeoInterface
        wvBrowser.post(presenter::nextPage);
    }

    @JavascriptInterface
    public void PrevPage() { // NeoInterface
        wvBrowser.post(presenter::prevPage);
    }

    @Override
    public void tipEndList() {
        Lib.showToast(getString(R.string.tip_end_list));
    }

    public void initSearch() {
        if (helper.isSearch())
            findText(helper.getCurrentSearch());
    }

    @Override
    public void openPage(@NonNull String url) {
        wvBrowser.post(() -> {
            wvBrowser.loadUrl(url);
        });
    }

    @Override
    public void isOtrkSite() {
        miRefresh.setVisible(false);
        miShare.setVisible(false);
    }

    @Override
    public void startLoading() {
        wvBrowser.clearCache(true);
        presenter.restoreStyle();
        status.setLoad(true);
        status.loadText();
    }

    @Override
    public void endLoading() {
        wvBrowser.post(() -> {
            if (status.isVisible())
                status.setLoad(false);
        });
    }

    @Override
    public void checkTime(long timeInSeconds) {
        status.checkTime(timeInSeconds);
    }

    public void openLink(String url) {
        presenter.openLink(url, true);
    }

    public void onBack() {
        presenter.openPage(false);
    }

    @Override
    public void onError(@NonNull Throwable throwable) {
        status.setError(throwable.getLocalizedMessage());
    }
}