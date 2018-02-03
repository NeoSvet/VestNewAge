package ru.neosvet.utils;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.SlashActivity;

public class Lib {
    private final String MAIN = "main", VER = "ver";
    private Context context;
    private SharedPreferences pref = null;

    public Lib(Context context) {
        this.context = context;
    }

    public static void LOG(String msg) {
        Log.d("neotag", msg);
    }

    public static void showToast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }

    public void setProtected(String value) {
        if (pref == null)
            pref = context.getSharedPreferences(MAIN, context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString(Const.COOKIE, value);
        editor.apply();
    }

    public String getProtected() {
        if (pref == null)
            pref = context.getSharedPreferences(MAIN, context.MODE_PRIVATE);
        return pref.getString(Const.COOKIE, "");
    }

    public int getPreviosVer() {
        if (pref == null)
            pref = context.getSharedPreferences(MAIN, context.MODE_PRIVATE);
        int prev = pref.getInt(VER, 0);
        try {
            int cur = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
            if (prev < cur) {
                SharedPreferences.Editor editor = pref.edit();
                editor.putInt(VER, cur);
                editor.apply();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return prev;
    }

    public static OkHttpClient createHttpClient() {
        OkHttpClient.Builder builderClient = new OkHttpClient.Builder();
        builderClient.connectTimeout(Const.TIMEOUT, TimeUnit.SECONDS);
        builderClient.readTimeout(Const.TIMEOUT, TimeUnit.SECONDS);
        builderClient.writeTimeout(Const.TIMEOUT, TimeUnit.SECONDS);
        return builderClient.build();
    }

    public InputStream getStream(String url) throws Exception {
        Request.Builder builderRequest = new Request.Builder();
        builderRequest.url(url);
        builderRequest.header(Const.USER_AGENT, context.getPackageName());
        if (url.contains(Const.SITE)) {
            builderRequest.header("Referer", Const.SITE);
            builderRequest.addHeader(Const.COOKIE, getProtected());
        }
        OkHttpClient client = createHttpClient();
        Response response;
        try {
            response = client.newCall(builderRequest.build()).execute();
        } catch (UnknownHostException e) {
            builderRequest.url(url.replace(Const.SITE, Const.SITE2));
            response = client.newCall(builderRequest.build()).execute();
        }
        if (url.contains(Const.SITE)) {
            InputStream in = new BufferedInputStream(response.body().byteStream());
            BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
            String s = br.readLine();
            br.close();
            if(s.contains("aes.js")) {
                setProtected("");
                Intent app = new Intent(context, SlashActivity.class);
                app.setData(Uri.parse(url));
                context.startActivity(app);
                throw new Exception("Fail protect");
            }
            response = client.newCall(builderRequest.build()).execute();
        }

        return response.body().byteStream();
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

    public String getDiffDate(long t1, long t2) {
        t2 = (t1 - t2) / 1000;
        int k;
        if (t2 < 60) {
            if (t2 == 0)
                t2 = 1;
            k = 0;
        } else {
            t2 = t2 / 60;
            if (t2 < 60)
                k = 3;
            else {
                t2 = t2 / 60;
                if (t2 < 24)
                    k = 6;
                else {
                    t2 = t2 / 24;
                    k = 9;
                }
            }
        }
        String time;
        if (t2 > 4 && t2 < 21)
            time = t2 + context.getResources().getStringArray(R.array.time)[1 + k];
        else {
            if (t2 == 1)
                time = context.getResources().getStringArray(R.array.time)[k];
            else {
                int n = (int) t2 % 10;
                if (n == 1)
                    time = t2 + " " + context.getResources().getStringArray(R.array.time)[k];
                else if (n > 1 && n < 5)
                    time = t2 + context.getResources().getStringArray(R.array.time)[2 + k];
                else
                    time = t2 + context.getResources().getStringArray(R.array.time)[1 + k];
            }
        }

        return time;
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
        s = s.replace("&ldquo;", "“").replace("&rdquo;", "”").replace(Const.BR, Const.N)
                .replace("&laquo;", "«").replace("&raquo;", "»").replace(Const.N + " ", Const.N)
                .replace("&ndash;", "–").replace("&nbsp;", " ");

        while ((i = s.indexOf("<")) > -1) {
            s = s.substring(0, i) + s.substring(s.indexOf(">", i) + 1);
        }
        return s.trim().replace("&gt;", ">");
    }
}
