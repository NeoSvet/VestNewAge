package ru.neosvet.vestnewage.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import ru.neosvet.ui.StatusButton;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.model.CabModel;

public class CabpageActivity extends AppCompatActivity {
    private final String HOST = "http://0s.o53xo.n52gw4tpozsw42lzmexgk5i.cmle.ru", SCRIPT =
            "var id=setInterval(';',1); for(var i=0;i<id;i++) window.clearInterval(i); var s=document.getElementById('rcol').innerHTML;s=s.substring(s.indexOf('/d')+5);s=s.substring(0,s.indexOf('hr2')-12);document.body.innerHTML='<div id=\"rcol\" style=\"padding-top:10px\" name=\"top\">'+s+'</div>';";
    //div main, d31-d35 - for stop log I/chromium: [INFO:CONSOLE(13)] "Uncaught TypeError:...
    private WebView wvBrowser;
    private StatusButton status;
    private View fabClose;
    private boolean twoPointers = false;

    public static void openPage(Context context, String link) {
        Intent intent = new Intent(context, CabpageActivity.class);
        intent.putExtra(Const.LINK, link);
        if (!(context instanceof Activity))
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cabpage_activity);
        initView();
        if (CabModel.cookie != null) {
            CookieSyncManager cookieSyncManager = CookieSyncManager.createInstance(wvBrowser.getContext());
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            cookieManager.removeSessionCookie();
            cookieManager.setCookie(HOST, CabModel.cookie + "; domain=" + HOST.substring(7));
            cookieSyncManager.getInstance().sync();
//            cookieManager.getCookie(HOST);
        }
        wvBrowser.loadUrl(HOST + "/" + getIntent().getStringExtra(Const.LINK));
    }

    private void initView() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setVisibility(View.VISIBLE);
        this.setTitle("");
        wvBrowser = (WebView) findViewById(R.id.wvBrowser);
        wvBrowser.getSettings().setJavaScriptEnabled(true);
        wvBrowser.setWebViewClient(new wvClient());
        wvBrowser.clearCache(true);
        wvBrowser.clearHistory();
        wvBrowser.getSettings().setBuiltInZoomControls(true);
        wvBrowser.getSettings().setDisplayZoomControls(false);
        if (android.os.Build.VERSION.SDK_INT > 18) {
            wvBrowser.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    if (event.getPointerCount() == 2) {
                        twoPointers = true;
                    } else if (twoPointers) {
                        twoPointers = false;
                        wvBrowser.setInitialScale((int) (wvBrowser.getScale() * 100.0));
                    }
                    return false;
                }
            });
        }
        status = new StatusButton(this, findViewById(R.id.pStatus));
        fabClose = findViewById(R.id.fabClose);
        fabClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });
    }

    private class wvClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (url.contains("#")) return;
            fabClose.setVisibility(View.GONE);
            status.setLoad(true);
            CabpageActivity.this.setTitle("");
            view.setVisibility(View.GONE);
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (url.contains("#")) return;
            if (android.os.Build.VERSION.SDK_INT > 18) {
                wvBrowser.evaluateJavascript(SCRIPT,
                        new ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String s) {
                                wvBrowser.setVisibility(View.VISIBLE);
                            }
                        });
            } else {
                wvBrowser.loadUrl("javascript: " + SCRIPT + " return false;");
                wvBrowser.setVisibility(View.VISIBLE);
            }
            String s = wvBrowser.getTitle();
            if (!s.contains(":")) {
                status.setError(getResources().getString(R.string.load_fail));
                finish();
                return;
            }
            CabpageActivity.this.setTitle(s.substring(s.indexOf(":") + 3));
            status.setLoad(false);
            fabClose.setVisibility(View.VISIBLE);
            super.onPageFinished(view, url);
        }
    }
}
