package ru.neosvet.utils;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.Toast;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.File;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import ru.neosvet.vestnewage.R;

public class Lib {
    public static final int MAX_ON_PAGE = 15;
    public static final String SITE = "http://blagayavest.info/", N = "\n", NN = "\n\n",
            TASK = "task", COOKIE = "Cookie", SESSION_ID = "PHPSESSID", FIRST = "first",
            TIME_LAST_VISIT = "time_last_visit", NOREAD = "noread", LINK = "<link>",
            LIGHT = "/style/light.css", DARK = "/style/dark.css", AND = "&", BR = "<br>",
            PRINT = "?styletpl=print", HREF = "href", DIALOG = "dialog",
            KV_OPEN = "“", KV_CLOSE = "”", POEMS = "poems";
    private Context context;

    public Lib(Context context) {
        this.context = context;
    }

    public void setCookies(String s, String t, String n) {
        SharedPreferences pref = context.getSharedPreferences(COOKIE, context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        if (s != null)
            editor.putString(SESSION_ID, s);
        editor.putString(TIME_LAST_VISIT, t);
        editor.putString(NOREAD, n);
        editor.apply();
    }

    public String getCookies(boolean boolNoreadOnly) {
//        return SESSION_ID + "=vs50e3l2soiopvmsbk24rvkmu2; "
//                + TIME_LAST_VISIT + "=16.12.2016+14%3A50%3A22; "
//                + NOREAD + "=a%3A1%3A%7Bi%3A0%3Ba%3A2%3A%7Bs%3A2%3A%22id%22%3Bi%3A1398%3Bs%3A4%3A%22link%22%3Bs%3A14%3A%22poems%2F15.12.16%22%3B%7D%7D";
        SharedPreferences pref = context.getSharedPreferences(COOKIE, context.MODE_PRIVATE);
        if (pref.getString(SESSION_ID, "").equals(""))
            return "";
        else if (boolNoreadOnly)
            return pref.getString(NOREAD, "");
        else
            return SESSION_ID + "=" + pref.getString(SESSION_ID, "") + "; "
                    + TIME_LAST_VISIT + "=" + pref.getString(TIME_LAST_VISIT, "")
                    + "; " + NOREAD + "=" + pref.getString(NOREAD, "");
    }

    public long getTimeLastVisit() {
        SharedPreferences pref = context.getSharedPreferences(COOKIE, context.MODE_PRIVATE);
        String s = pref.getString(TIME_LAST_VISIT, "");
        if (s.equals(""))
            return 0;
        s = s.replace("%3A", ":").replace("+", " ");
        try {
            DateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
            Date d = df.parse(s);
            return d.getTime();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static void showToast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }

    public InputStream getStream(String url) throws Exception {
        DefaultHttpClient client = new DefaultHttpClient();
        HttpGet req = new HttpGet(url);
        HttpEntity entity;
        HttpResponse res;
        req.setHeader("User-Agent", context.getPackageName());
        if (url.contains(SITE) && !url.contains(PRINT)) {
            String s = getCookies(false);
            if (!s.equals(""))
                req.setHeader(COOKIE, s);
            res = client.execute(req);
            entity = res.getEntity();
            List<Cookie> cookies = client.getCookieStore().getCookies();
            if (!cookies.isEmpty()) {
                if (cookies.size() == 2) {
                    setCookies(null, cookies.get(0).getValue(),
                            cookies.get(1).getValue());
                } else if (cookies.size() == 3) {
                    setCookies(cookies.get(0).getValue(),
                            cookies.get(1).getValue(), cookies.get(2).getValue());
                }
            }
        } else {
            req.setHeader("Referer", SITE);
            res = client.execute(req);
            entity = res.getEntity();
        }
        return entity.getContent();
    }

    public File getDBFolder() {
        String s = context.getFilesDir().toString();
        File f = new File(s.substring(0, s.length() - 5) + "databases");
        return f;
    }

    public File getFile(String link) {
        File file = new File(context.getFilesDir() + link.substring(0, link.lastIndexOf("/")));
        if (!file.exists())
            file.mkdirs();
        file = new File(context.getFilesDir() + link);
        return file;
    }

    public static void LOG(String msg) {
        Log.d("tag", msg);
    }

    public void openInApps(String url, String titleChooser) {
        try {
            Intent myIntent = new Intent(Intent.ACTION_VIEW);
            myIntent.setData(android.net.Uri.parse(url));
            if (titleChooser == null)
                context.startActivity(myIntent);
            else
                context.startActivity(Intent.createChooser(myIntent, titleChooser));
        } catch (Exception e) {
            url = url.substring(url.indexOf(":") + 1);
            if (url.indexOf("/") == 0)
                url = url.substring(2);
            copyAddress(url);
        }
    }

    public void copyAddress(String txt) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(context.getResources().getText(R.string.app_name), txt);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(context, context.getResources().getText(R.string.address_copied), Toast.LENGTH_LONG).show();
    }

    public String getDiffDate(long now, long t) {
        t = (now - t) / 1000;
        int k;
        if (t < 60) {
            if (t == 0)
                t = 1;
            k = 0;
        } else {
            t = t / 60;
            if (t < 60)
                k = 3;
            else {
                t = t / 60;
                if (t < 24)
                    k = 6;
                else {
                    t = t / 24;
                    k = 9;
                }
            }
        }
        String time;
        if (t > 4 && t < 21)
            time = t + context.getResources().getStringArray(R.array.time)[1 + k];
        else {
            if (t == 1)
                time = context.getResources().getStringArray(R.array.time)[k];
            else {
                int n = (int) t % 10;
                if (n == 1)
                    time = t + " " + context.getResources().getStringArray(R.array.time)[k];
                else if (n > 1 && n < 5)
                    time = t + context.getResources().getStringArray(R.array.time)[2 + k];
                else
                    time = t + context.getResources().getStringArray(R.array.time)[1 + k];
            }
        }

        return time + context.getResources().getStringArray(R.array.time)[12];
    }

    public boolean verifyStoragePermissions(int code) {
        //http://stackoverflow.com/questions/38989050/android-6-0-write-to-external-sd-card
        int permission = ActivityCompat.checkSelfPermission(context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context,
                    new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    }, code);
            return true;
        }
        return false;
    }

    public static String withOutTags(String s) {
        int i;
        s = s.replace("&ldquo;", "“").replace("&rdquo;", "”").replace(BR, N)
                .replace("&laquo;", "«").replace("&raquo;", "»").replace(N + " ", N)
                .replace("&ndash;", "–").replace("&nbsp;", " ");

        while ((i = s.indexOf("<")) > -1) {
            s = s.substring(0, i) + s.substring(s.indexOf(">", i) + 1);
        }
        return s.trim().replace("&gt;", ">");
    }
}
