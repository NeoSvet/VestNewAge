package ru.neosvet.vestnewage.utils;

import android.os.Build;

import androidx.work.Data;

import java.security.cert.CertificateException;
import java.util.Map;

import javax.net.ssl.SSLHandshakeException;

import ru.neosvet.vestnewage.App;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.data.BaseIsBusyException;

public class ErrorUtils {
    private static Throwable error = null;
    private static Data data;

    public static void clear() {
        data = null;
        error = null;
    }

    public static void setData(Data data) {
        ErrorUtils.data = data;
    }

    public static void setError(Throwable error) {
        ErrorUtils.error = error;
    }

    public static String getMessage() {
        if (error == null) return "";
        if (error instanceof BaseIsBusyException)
            return App.context.getString(R.string.busy_base_error);
        String msg = error.getLocalizedMessage();
        if (msg == null || msg.isEmpty())
            return App.context.getString(R.string.unknown_error);
        if (msg.equals("timeout") || error instanceof SSLHandshakeException || error instanceof CertificateException)
            return App.context.getString(R.string.error_site);
        if (msg.contains("failed to connect")) {
            int i = msg.indexOf("connect") + 11;
            String site = msg.substring(i, msg.indexOf("/", i));
            i = msg.indexOf("after") + 6;
            String sec = msg.substring(i, i + 2);
            return String.format(App.context.getString(R.string.format_timeout), site, sec);
        }
        return msg;
    }

    public static String getInformation() {
        StringBuilder des = new StringBuilder();
        if (error != null) {
            des.append(App.context.getString(R.string.error_des));
            des.append(Const.N);
            des.append(error.getMessage());
            des.append(Const.N);
            for (StackTraceElement e : error.getStackTrace()) {
                String s = e.toString();
                if (s.contains("ru.neosvet")) {
                    des.append(s);
                    des.append(Const.N);
                }
            }
        }
        if (data != null) {
            des.append(Const.N);
            des.append(App.context.getString(R.string.input_data));
            des.append(Const.N);
            Map<String, Object> map = data.getKeyValueMap();
            for (String key : map.keySet()) {
                des.append(key);
                des.append(": ");
                des.append(map.get(key));
                des.append(Const.N);
            }
        }
        try {
            des.append(App.context.getString(R.string.srv_info));
            des.append(String.format(App.context.getString(R.string.format_info),
                    App.context.getPackageManager().getPackageInfo(App.context.getPackageName(), 0).versionName,
                    App.context.getPackageManager().getPackageInfo(App.context.getPackageName(), 0).versionCode,
                    Build.VERSION.RELEASE,
                    Build.VERSION.SDK_INT));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return des.toString();
    }
}
