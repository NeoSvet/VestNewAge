package ru.neosvet.vestnewage;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import ru.neosvet.ui.StatusBar;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;

public class CabpageActivity extends AppCompatActivity {
    private final String HOST = "http://o53xo.n52gw4tpozsw42lzmexgk5i.cmle.ru", SCRIPT =
            "var id=setInterval(';',1); for(var i=0;i<id;i++) window.clearInterval(i); var s=document.getElementById('rcol').innerHTML;s=s.substring(s.indexOf('/d')+5);s=s.substring(0,s.indexOf('hr2')-12);document.body.innerHTML='<div id=\"rcol\" style=\"padding-top:10px\" name=\"top\">'+s+'</div>';";
    //div main, d31-d35 - for stop log I/chromium: [INFO:CONSOLE(13)] "Uncaught TypeError:...
    private WebView wvBrowser;
    private StatusBar status;

    public static void openPage(Context context, String link, String cookie) {
        Intent intent = new Intent(context, CabpageActivity.class);
        intent.putExtra(DataBase.LINK, link);
        intent.putExtra(Lib.COOKIE, cookie);
        if (!(context instanceof Activity))
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cabpage_activity);
        initView();
        String cookie = getIntent().getStringExtra(Lib.COOKIE);
        if (cookie != null) {
            CookieSyncManager cookieSyncManager = CookieSyncManager.createInstance(wvBrowser.getContext());
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            cookieManager.removeSessionCookie();
            cookieManager.setCookie(HOST, cookie + "; domain=" + HOST.substring(7));
            cookieSyncManager.getInstance().sync();
//            cookieManager.getCookie(HOST);
        }
        wvBrowser.loadUrl(HOST + "/" + getIntent().getStringExtra(DataBase.LINK));
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
        status = new StatusBar(this, findViewById(R.id.pStatus));
    }

    private class wvClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (url.contains("#")) return;
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
            CabpageActivity.this.setTitle(s.substring(s.indexOf(":") + 3));
            status.setLoad(false);
            super.onPageFinished(view, url);
        }
    }
}
