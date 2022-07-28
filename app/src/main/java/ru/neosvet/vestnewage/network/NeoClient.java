package ru.neosvet.vestnewage.network;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.neosvet.vestnewage.App;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.data.MyException;
import ru.neosvet.vestnewage.utils.ErrorUtils;
import ru.neosvet.vestnewage.utils.Lib;

public class NeoClient {
    private static boolean first = true;

    public static boolean isMainSite() {
        return first;
    }

    public static OkHttpClient createHttpClient() {
        OkHttpClient.Builder client = new OkHttpClient.Builder();
        client.connectTimeout(NetConst.TIMEOUT, TimeUnit.SECONDS);
        client.readTimeout(NetConst.TIMEOUT, TimeUnit.SECONDS);
        client.writeTimeout(NetConst.TIMEOUT, TimeUnit.SECONDS);
        return client.build();
    }

    public static BufferedInputStream getStream(String url) throws Exception {
        if (!first && url.contains(NetConst.SITE))
            url = url.replace(NetConst.SITE, NetConst.SITE2);

        Response response;
        try {
            Request.Builder builderRequest = new Request.Builder();
            builderRequest.url(url);
            builderRequest.header(NetConst.USER_AGENT, App.context.getPackageName());
            if (url.contains(NetConst.SITE)) {
                builderRequest.header("Referer", NetConst.SITE);
            }
            OkHttpClient client = createHttpClient();
            response = client.newCall(builderRequest.build()).execute();
        } catch (Exception e) {
            ErrorUtils.setError(e);
            e.printStackTrace();
            if (url.contains(NetConst.SITE)) {
                first = false;
                return getStream(url.replace(NetConst.SITE, NetConst.SITE2));
            } else
                throw new MyException(App.context.getString(R.string.error_site));
        }

        if (response.code() != 200) {
            if (url.contains(NetConst.SITE)) {
                first = false;
                return getStream(url.replace(NetConst.SITE, NetConst.SITE2));
            } else
                throw new MyException(App.context.getString(R.string.error_code)
                        + response.code());
        }

        if (response.body() == null)
            throw new MyException(App.context.getString(R.string.error_site));
        InputStream inStream = response.body().byteStream();
        File file = Lib.getFileP("/cache/file");
        if (file.exists()) file.delete();
        FileOutputStream outStream = new FileOutputStream(file);
        byte[] buffer = new byte[1024];
        int length = inStream.read(buffer);
        while (length > 0) {
            outStream.write(buffer, 0, length);
            length = inStream.read(buffer);
        }
        inStream.close();
        outStream.close();
        return new BufferedInputStream(new FileInputStream(file));
    }
}
