package ru.neosvet.utils;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.neosvet.vestnewage.App;
import ru.neosvet.vestnewage.R;

public class Lib {
    private boolean first = true;

    public boolean isMainSite() {
        return first;
    }

//    public static void LOG(String msg) {
//        Log.d("neotag", msg);
//    }

    public static void showToast(String msg) {
        Toast.makeText(App.context, msg, Toast.LENGTH_LONG).show();
    }

    public static OkHttpClient createHttpClient() {
        OkHttpClient.Builder client = new OkHttpClient.Builder();
        client.connectTimeout(Const.TIMEOUT, TimeUnit.SECONDS);
        client.readTimeout(Const.TIMEOUT, TimeUnit.SECONDS);
        client.writeTimeout(Const.TIMEOUT, TimeUnit.SECONDS);
        return client.build();
    }

    public InputStream getStream(String url) throws Exception {
        if (!first && url.contains(Const.SITE))
            url = url.replace(Const.SITE, Const.SITE2);

        Response response;
        try {
            Request.Builder builderRequest = new Request.Builder();
            builderRequest.url(url);
            builderRequest.header(Const.USER_AGENT, App.context.getPackageName());
            if (url.contains(Const.SITE)) {
                builderRequest.header("Referer", Const.SITE);
            }
            OkHttpClient client = createHttpClient();
            response = client.newCall(builderRequest.build()).execute();
        } catch (Exception e) {
            ErrorUtils.setError(e);
            e.printStackTrace();
            if (url.contains(Const.SITE)) {
                first = false;
                return getStream(url.replace(Const.SITE, Const.SITE2));
            } else
                throw new MyException(App.context.getString(R.string.error_site));
        }

        if (response.code() != 200) {
            if (url.contains(Const.SITE)) {
                first = false;
                return getStream(url.replace(Const.SITE, Const.SITE2));
            } else
                throw new MyException(App.context.getString(R.string.error_code)
                        + response.code());
        }

        if (response.body() == null)
            throw new MyException(App.context.getString(R.string.error_site));
        return response.body().byteStream();
    }

    public File getDBFolder() {
        String s = App.context.getFilesDir().toString();
        return new File(s.substring(0, s.length() - 5) + "databases");
    }

    public File getFile(String link) {
        File file = getFileByName(link.substring(0, link.lastIndexOf("/")));
        if (!file.exists())
            file.mkdirs();
        file = getFileByName(link);
        return file;
    }

    public static File getFileByName(String name) {
        return new File(App.context.getFilesDir() + name);
    }

    public void openInApps(String url, @Nullable String titleChooser) {
        try {
            Intent myIntent = new Intent(Intent.ACTION_VIEW);
            myIntent.setData(android.net.Uri.parse(url));
            if (titleChooser == null)
                App.context.startActivity(myIntent);
            else
                App.context.startActivity(Intent.createChooser(myIntent, titleChooser));
        } catch (Exception e) {
            url = url.substring(url.indexOf(":") + 1);
            if (url.indexOf("/") == 0)
                url = url.substring(2);
            copyAddress(url);
        }
    }

    public void copyAddress(String txt) {
        ClipboardManager clipboard = (ClipboardManager) App.context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(App.context.getString(R.string.app_name), txt);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(App.context, App.context.getString(R.string.address_copied), Toast.LENGTH_LONG).show();
    }

    public static String withOutTags(String s) {
        return android.text.Html.fromHtml(s).toString().trim();
    }
}
