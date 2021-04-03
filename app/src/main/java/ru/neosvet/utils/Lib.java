package ru.neosvet.utils;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.TlsVersion;
import ru.neosvet.vestnewage.R;

public class Lib {
    private final Context context;

    public Lib(Context context) {
        this.context = context;
    }

//    public static void LOG(String msg) {
//        Log.d("neotag", msg);
//    }

    public static void showToast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }

    public static OkHttpClient createHttpClient() throws NoSuchAlgorithmException, KeyManagementException {
        OkHttpClient.Builder client = new OkHttpClient.Builder();

        if (Build.VERSION.SDK_INT < 22) {
            SSLContext sc = SSLContext.getInstance("TLSv1.2");
            sc.init(null, null, null);
            client.sslSocketFactory(new Tls12SocketFactory(sc.getSocketFactory()));
            List<ConnectionSpec> specs = new LinkedList<>();
            specs.add(new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2).build());
            specs.add(ConnectionSpec.COMPATIBLE_TLS);
            specs.add(ConnectionSpec.CLEARTEXT);
            client.connectionSpecs(specs);
        }

        client.connectTimeout(Const.TIMEOUT, TimeUnit.SECONDS);
        client.readTimeout(Const.TIMEOUT, TimeUnit.SECONDS);
        client.writeTimeout(Const.TIMEOUT, TimeUnit.SECONDS);
        return client.build();
    }

    public InputStream getStream(String url) throws Exception {
        Request.Builder builderRequest = new Request.Builder();
        builderRequest.url(url);
        builderRequest.header(Const.USER_AGENT, context.getPackageName());
        if (url.contains(Const.SITE)) {
            builderRequest.header("Referer", Const.SITE);
        }

        OkHttpClient client = createHttpClient();
        Response response = client.newCall(builderRequest.build()).execute();

        return response.body().byteStream();
    }

    public File getDBFolder() {
        String s = context.getFilesDir().toString();
        return new File(s.substring(0, s.length() - 5) + "databases");
    }

    public File getFile(String link) {
        File file = new File(context.getFilesDir() + link.substring(0, link.lastIndexOf("/")));
        if (!file.exists())
            file.mkdirs();
        file = new File(context.getFilesDir() + link);
        return file;
    }

    public File getFileByName(String name) {
        return new File(context.getFilesDir() + name);
    }

    public void openInApps(String url, @Nullable String titleChooser) {
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

    public boolean verifyStoragePermissions(int code) {
        //http://stackoverflow.com/questions/38989050/android-6-0-write-to-external-sd-card
        int permission = ActivityCompat.checkSelfPermission(context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context,
                    new String[]{
                            //Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    }, code);
            return true;
        }
        return false;
    }

    public static String withOutTags(String s) {
        return android.text.Html.fromHtml(s).toString().trim();
    }

    public String getWorkSite() throws Exception {
        InputStream in = new BufferedInputStream(getStream(Const.SITE));
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        String s;
        boolean first = false;
        while ((s = br.readLine()) != null) {
            if (s.contains("title")) {
                first = s.contains("Благая Весть");
                break;
            }
        }
        br.close();
        in.close();
        if (first)
            return Const.SITE;
        else
            return Const.SITE2;
    }
}
