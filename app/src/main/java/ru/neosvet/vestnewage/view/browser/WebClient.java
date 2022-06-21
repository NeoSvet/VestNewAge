package ru.neosvet.vestnewage.view.browser;

import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Timer;
import java.util.TimerTask;

import ru.neosvet.vestnewage.utils.Lib;
import ru.neosvet.vestnewage.view.activity.BrowserActivity;

public class WebClient extends WebViewClient {
    private final String files = "file";
    private BrowserActivity act;

    public WebClient(BrowserActivity act) {
        this.act = act;
    }

    private String getUrl(String url) {
        if (url.contains(act.getPackageName())) // страница во внутренем хранилище
            url = url.substring(url.indexOf("age") + 10);
        else
            url = url.substring(url.indexOf(files) + 8);
        return url;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        view.setVisibility(View.GONE);
        if (url.contains(files)) {
            act.openLink(getUrl(url));
            return true;
        }
        if (url.contains("http") || url.contains("mailto")) {
            act.onBack();
            Lib.openInApps(url, null);
        } else
            act.openLink(url);
//        super.shouldOverrideUrlLoading(view, url);
        return true;
    }

    public void onPageFinished(WebView view, String url) {
        view.setVisibility(View.VISIBLE);
        act.onPageFinished(url.contains(files));
    }
}
