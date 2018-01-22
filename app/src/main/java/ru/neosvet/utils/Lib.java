package ru.neosvet.utils;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.neosvet.vestnewage.R;

public class Lib {
    private Context context;

    public Lib(Context context) {
        this.context = context;
    }

    public static void LOG(String msg) {
        Log.d("tag", msg);
    }

    public static void showToast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
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
        builderRequest.header("Referer", Const.SITE);
        OkHttpClient client = createHttpClient();
        Response response = client.newCall(builderRequest.build()).execute();

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
        s = s.replace("&ldquo;", "“").replace("&rdquo;", "”").replace(Const.BR, Const.N)
                .replace("&laquo;", "«").replace("&raquo;", "»").replace(Const.N + " ", Const.N)
                .replace("&ndash;", "–").replace("&nbsp;", " ");

        while ((i = s.indexOf("<")) > -1) {
            s = s.substring(0, i) + s.substring(s.indexOf(">", i) + 1);
        }
        return s.trim().replace("&gt;", ">");
    }
}
