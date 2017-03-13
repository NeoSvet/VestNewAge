package ru.neosvet.ui;

import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Timer;
import java.util.TimerTask;

import ru.neosvet.vestnewage.BrowserActivity;

public class WebClient extends WebViewClient {
    private final String files = "file";
    private BrowserActivity act;

    public WebClient(BrowserActivity act) {
        this.act = act;
    }

    private String getUrl(String url) {
        url = url.substring(url.indexOf(files, 10) + 8);
        if (url.contains(act.getPackageName()))
            url = url.substring(url.indexOf("age") + 4);
        return url;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        //Lib.LOG("shouldOverrideUrlLoading1=" + url);
        view.setVisibility(View.GONE);
        if (url.contains(files)) {
            url = getUrl(url);
            act.newLink(url);
            if (url.contains(BrowserActivity.PNG))
                act.openFile();
            else
                act.openPage(true);
            return true;
        }
//        Lib.LOG("shouldOverrideUrlLoading2=" + url);
        if (url.contains("http") || url.contains("mailto"))
            act.openInApps(url);
        else
            act.openLink(url);
//        super.shouldOverrideUrlLoading(view, url);
        return true;
    }

    public void onPageFinished(WebView view, String url) {
//        Lib.LOG("onPageFinished=" + url);
        view.setVisibility(View.VISIBLE);
        if (url.contains(files)) {
            act.newLink(getUrl(url));
            if (!url.contains(BrowserActivity.PNG))
                act.addJournal();
        }
        if (android.os.Build.VERSION.SDK_INT > 15)
            act.setPlace();
        else {
            final Handler hPlace = new Handler(new Handler.Callback() {
                @Override
                public boolean handleMessage(Message message) {
                    act.setPlace();
                    return false;
                }
            });
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    hPlace.sendEmptyMessage(0);
                }
            }, 500);
        }
    }
}
