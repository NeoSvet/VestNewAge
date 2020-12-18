package ru.neosvet.ui;

import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Timer;
import java.util.TimerTask;

import ru.neosvet.vestnewage.activity.BrowserActivity;

public class WebClient extends WebViewClient {
    private final String files = "file";
    private BrowserActivity act;
    private Handler hPlace = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            act.setPlace();
            return false;
        }
    });

    public WebClient(BrowserActivity act) {
        this.act = act;
    }

    private String getUrl(String url) {
        if (url.contains(act.getPackageName())) // страница во внутренем хранилище
            url = url.substring(url.indexOf("age") + 4);
        else
            url = url.substring(url.indexOf(files) + 8);
        return url;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        view.setVisibility(View.GONE);
        if (url.contains(files)) {
            act.openLink(getUrl(url), true);
            return true;
        }
        if (url.contains("http") || url.contains("mailto")) {
            act.openPage(false);
            act.openInApps(url);
        } else
            act.openLink(url, true);
//        super.shouldOverrideUrlLoading(view, url);
        return true;
    }

    public void onPageFinished(WebView view, String url) {
        view.setVisibility(View.VISIBLE);
        if (url.contains(files)) {
            act.checkUnread();
            act.addJournal();
        }
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                hPlace.sendEmptyMessage(0);
            }
        }, 500);
    }
}
