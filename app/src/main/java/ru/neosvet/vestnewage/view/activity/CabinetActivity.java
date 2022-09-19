package ru.neosvet.vestnewage.view.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.InputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.neosvet.vestnewage.App;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.helper.CabinetHelper;
import ru.neosvet.vestnewage.network.NetConst;
import ru.neosvet.vestnewage.network.UnsafeClient;
import ru.neosvet.vestnewage.utils.Const;
import ru.neosvet.vestnewage.view.basic.StatusButton;
import ru.neosvet.vestnewage.view.dialog.CustomDialog;

public class CabinetActivity extends AppCompatActivity {
    private WebView wvBrowser;
    private StatusButton status;
    private View fabClose;
    private boolean twoPointers = false;

    public static void openPage(String link) {
        Intent intent = new Intent(App.context, CabinetActivity.class);
        intent.putExtra(Const.LINK, link);
        if (!(App.context instanceof Activity))
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        App.context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cabinet_activity);
        initView();
        status.setLoad(true);
        wvBrowser.loadUrl(NetConst.SITE_COM + getIntent().getStringExtra(Const.LINK));
    }

    @Override
    protected void onDestroy() {
        wvBrowser.stopLoading();
        super.onDestroy();
    }

    @SuppressLint({"ClickableViewAccessibility", "SetJavaScriptEnabled"})
    private void initView() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setVisibility(View.VISIBLE);
        this.setTitle("");
        wvBrowser = findViewById(R.id.wvBrowser);
        wvBrowser.getSettings().setJavaScriptEnabled(true);
        wvBrowser.setWebViewClient(new wvClient());
        wvBrowser.clearCache(true);
        wvBrowser.clearHistory();
        wvBrowser.getSettings().setBuiltInZoomControls(true);
        wvBrowser.getSettings().setDisplayZoomControls(false);
        wvBrowser.getSettings().setAllowContentAccess(true);
        wvBrowser.getSettings().setAllowFileAccess(true);
        wvBrowser.setOnTouchListener((view, event) -> {
            if (event.getPointerCount() == 2) {
                twoPointers = true;
            } else if (twoPointers) {
                twoPointers = false;
                wvBrowser.setInitialScale((int) (wvBrowser.getScale() * 100.0));
            }
            return false;
        });
        status = new StatusButton(this, findViewById(R.id.pStatus));
        fabClose = findViewById(R.id.fabClose);
        fabClose.setOnClickListener(view -> onBackPressed());
    }

    private class wvClient extends WebViewClient {
        private static final String SCRIPT = "var id=setInterval(';',1); for(var i=0;i<id;i++) window.clearInterval(i); var s=document.getElementById('rcol').innerHTML;s=s.substring(s.indexOf('/d')+5);s=s.substring(0,s.indexOf('hr2')-12);document.body.innerHTML='<div id=\"rcol\" style=\"padding-top:10px\" name=\"top\">'+s+'</div>';";

        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (CabinetHelper.cookie.isEmpty())
                return super.shouldInterceptRequest(view, request);
            try {
                OkHttpClient client;
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M)
                    client = UnsafeClient.createHttpClient();
                else
                    client = new OkHttpClient();
                Request req = new Request.Builder()
                        .url(request.getUrl().toString())
                        .addHeader("cookie", CabinetHelper.cookie)
                        .build();
                Response response = client.newCall(req).execute();
                InputStream responseInputStream = response.body().byteStream();
                return new WebResourceResponse(null, null, responseInputStream);
            } catch (Exception e) {
                return super.shouldInterceptRequest(view, request);
            }
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (url.contains("#")) return;
            fabClose.setVisibility(View.GONE);
            status.setLoad(true);
            CabinetActivity.this.setTitle("");
            view.setVisibility(View.GONE);
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (url.contains("#") || CabinetActivity.this.isDestroyed()) return;
            wvBrowser.evaluateJavascript(SCRIPT, s -> status.setLoad(false));
            String s = wvBrowser.getTitle();
            fabClose.setVisibility(View.VISIBLE);
            if (!s.contains(":")) {
                final CustomDialog alert = new CustomDialog(CabinetActivity.this);
                alert.setTitle(getString(R.string.error));
                alert.setMessage(getString(R.string.cab_fail));
                alert.setRightButton(getString(android.R.string.ok), v -> alert.dismiss());
                alert.show(null);
                return;
            }
            wvBrowser.setVisibility(View.VISIBLE);
            CabinetActivity.this.setTitle(s.substring(s.indexOf(":") + 3));
            super.onPageFinished(view, url);
        }
    }
}
