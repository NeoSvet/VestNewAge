package ru.neosvet.vestnewage.network;

import java.io.BufferedInputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.neosvet.vestnewage.App;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.data.MyException;
import ru.neosvet.vestnewage.utils.Const;
import ru.neosvet.vestnewage.utils.ErrorUtils;

public class NeoClient {
    public static final String SITE = "https://blagayavest.info/", SITE2 = "https://chenneling.info/",
            CAB_SITE = "https://www.otkroveniya.eu/", USER_AGENT = "User-Agent",
            COOKIE = "Cookie", SET_COOKIE = "Set-Cookie";
    private static boolean first = true;

    public static boolean isMainSite() {
        return first;
    }

    public static OkHttpClient createHttpClient() {
        OkHttpClient.Builder client = new OkHttpClient.Builder();
        client.connectTimeout(Const.TIMEOUT, TimeUnit.SECONDS);
        client.readTimeout(Const.TIMEOUT, TimeUnit.SECONDS);
        client.writeTimeout(Const.TIMEOUT, TimeUnit.SECONDS);
        return client.build();
    }

    public static BufferedInputStream getStream(String url) throws Exception {
        if (!first && url.contains(SITE))
            url = url.replace(SITE, SITE2);

        Response response;
        try {
            Request.Builder builderRequest = new Request.Builder();
            builderRequest.url(url);
            builderRequest.header(USER_AGENT, App.context.getPackageName());
            if (url.contains(SITE)) {
                builderRequest.header("Referer", SITE);
            }
            OkHttpClient client = createHttpClient();
            response = client.newCall(builderRequest.build()).execute();
        } catch (Exception e) {
            ErrorUtils.setError(e);
            e.printStackTrace();
            if (url.contains(SITE)) {
                first = false;
                return getStream(url.replace(SITE, SITE2));
            } else
                throw new MyException(App.context.getString(R.string.error_site));
        }

        if (response.code() != 200) {
            if (url.contains(SITE)) {
                first = false;
                return getStream(url.replace(SITE, SITE2));
            } else
                throw new MyException(App.context.getString(R.string.error_code)
                        + response.code());
        }

        if (response.body() == null)
            throw new MyException(App.context.getString(R.string.error_site));
        return new BufferedInputStream(response.body().byteStream());
    }
}
