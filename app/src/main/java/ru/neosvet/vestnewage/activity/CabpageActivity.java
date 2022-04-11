package ru.neosvet.vestnewage.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
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
import ru.neosvet.ui.StatusButton;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.NeoClient;
import ru.neosvet.vestnewage.App;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.fragment.CabmainFragment;
import ru.neosvet.vestnewage.model.CabModel;

public class CabpageActivity extends AppCompatActivity {
    private WebView wvBrowser;
    private StatusButton status = new StatusButton();
    private View fabClose;
    private boolean twoPointers = false;

    public static void openPage(String link) {
        Intent intent = new Intent(App.context, CabpageActivity.class);
        intent.putExtra(Const.LINK, link);
        if (!(App.context instanceof Activity))
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        App.context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cabpage_activity);
        initView();
        wvBrowser.loadUrl(NeoClient.CAB_SITE + getIntent().getStringExtra(Const.LINK));
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
        status.init(this, findViewById(R.id.pStatus));
        fabClose = findViewById(R.id.fabClose);
        fabClose.setOnClickListener(view -> onBackPressed());
    }

    private class wvClient extends WebViewClient {
        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (CabModel.cookie == null || CabModel.cookie.isEmpty())
                return super.shouldInterceptRequest(view, request);
            try {
                OkHttpClient client = new OkHttpClient();
                Request req = new Request.Builder()
                        .url(request.getUrl().toString())
                        .addHeader("cookie", CabModel.cookie)
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
            CabpageActivity.this.setTitle("");
            view.setVisibility(View.GONE);
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (url.contains("#")) return;
            String SCRIPT = "var id=setInterval(';',1); for(var i=0;i<id;i++) window.clearInterval(i); var s=document.getElementById('rcol').innerHTML;s=s.substring(s.indexOf('/d')+5);s=s.substring(0,s.indexOf('hr2')-12);document.body.innerHTML='<div id=\"rcol\" style=\"padding-top:10px\" name=\"top\">'+s+'</div>';";
            wvBrowser.evaluateJavascript(SCRIPT,
                    s -> wvBrowser.setVisibility(View.VISIBLE));
            status.setLoad(false);
            String s = wvBrowser.getTitle();
            if (!s.contains(":")) {
                CabmainFragment.error = getString(R.string.cab_fail);
                onBackPressed();
                return;
            }
            CabpageActivity.this.setTitle(s.substring(s.indexOf(":") + 3));
            fabClose.setVisibility(View.VISIBLE);
            super.onPageFinished(view, url);
        }
    }
}
