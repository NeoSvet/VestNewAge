package ru.neosvet.vestnewage.utils;

import android.os.Build;

import androidx.work.Data;

import java.util.Map;

import ru.neosvet.vestnewage.App;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.data.MyException;

public class ErrorUtils {
    private static Exception error;
    private static Data data;

    public static boolean isNotEmpty() {
        return data != null || error != null;
    }

    public static String getMessage() {
        if (error == null) return "";
        return error.getLocalizedMessage();
    }

    public static void clear() {
        data = null;
        error = null;
    }

    public static void setData(Data data) {
        ErrorUtils.data = data;
    }

    public static void setError(Exception error) {
        if (!(error instanceof MyException))
            ErrorUtils.error = error;
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
