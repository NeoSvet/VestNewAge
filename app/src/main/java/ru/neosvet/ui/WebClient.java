package ru.neosvet.ui;

import android.webkit.WebView;
import android.webkit.WebViewClient;

import ru.neosvet.blagayavest.BrowserActivity;

/**
 * Created by NeoSvet on 22.12.2016.
 */
public class WebClient extends WebViewClient {
    private final String files = "file";
    private BrowserActivity act;

    public WebClient(BrowserActivity act) {
        this.act = act;
    }

    private String getUrl(String url) {
        url = url.substring(url.indexOf(files, 10) + 6);
        return url;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
//        Lib.LOG("shouldOverrideUrlLoading1=" + url);
        if (url.contains(files)) {
            url = getUrl(url);
            if (url.contains(BrowserActivity.PNG)) {
                act.newLink(url);
                act.openFile();
                return true;
            }
        }
//        Lib.LOG("shouldOverrideUrlLoading2=" + url);
        if (url.contains("http") || url.contains("mailto"))
            act.openInApps(url);
        else
            act.openLink(url);
//        super.shouldOverrideUrlLoading(view, url);
        return false;
    }

    public void onPageFinished(WebView view, String url) {
//        Lib.LOG("onPageFinished=" + url);
        if (url.contains(files)) {
            act.newLink(getUrl(url));
            if (!url.contains(BrowserActivity.PNG))
                act.addJournal();
        }
    }
}
