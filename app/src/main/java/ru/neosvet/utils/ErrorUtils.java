package ru.neosvet.utils;

import android.content.Context;
import android.os.Build;

import androidx.work.Data;

import java.util.Map;

import ru.neosvet.vestnewage.R;

public class ErrorUtils {
    private static Exception error;
    private static Data data;

    public static void clear() {
        ErrorUtils.data = null;
        ErrorUtils.error = null;
    }

    public static void setData(Data data) {
        ErrorUtils.data = data;
    }

    public static void setError(Exception error) {
        if (!(error instanceof MyException))
            ErrorUtils.error = error;
    }

    public static String getInformation(Context context) {
        StringBuilder des = new StringBuilder();
        if (ErrorUtils.error != null) {
            des.append(context.getString(R.string.error_des));
            des.append(Const.N);
            des.append(ErrorUtils.error.getMessage());
            des.append(Const.N);
            for (StackTraceElement e : ErrorUtils.error.getStackTrace()) {
                String s = e.toString();
                if (s.contains("ru.neosvet")) {
                    des.append(s);
                    des.append(Const.N);
                }
            }
        }
        if (ErrorUtils.data != null) {
            des.append(Const.N);
            des.append(context.getString(R.string.input_data));
            des.append(Const.N);
            Map<String, Object> map = ErrorUtils.data.getKeyValueMap();
            for (String key : map.keySet()) {
                des.append(key);
                des.append(": ");
                des.append(map.get(key));
                des.append(Const.N);
            }
        }
        try {
            des.append(context.getResources().getString(R.string.srv_info));
            des.append(context.getResources().getString(R.string.app_version));
            des.append(context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName);
            des.append(" (");
            des.append(context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode);
            des.append(")\n");
            des.append(context.getResources().getString(R.string.system_version));
            des.append(Build.VERSION.RELEASE);
            des.append(" (");
            des.append(Build.VERSION.SDK_INT);
            des.append(")");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return des.toString();
    }
}
