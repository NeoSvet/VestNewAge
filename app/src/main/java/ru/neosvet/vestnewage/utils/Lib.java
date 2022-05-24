package ru.neosvet.vestnewage.utils;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.io.File;

import ru.neosvet.vestnewage.App;
import ru.neosvet.vestnewage.R;

public class Lib {
//    public static void LOG(String msg) {
//        Log.d("neotag", msg);
//    }

    public static void showToast(String msg) {
        Toast.makeText(App.context, msg, Toast.LENGTH_LONG).show();
    }

    public static File getFile(String name) {
        return new File(App.context.getFilesDir() + name);
    }

    public static File getFileS(String name) {
        return new File(App.context.getFilesDir() + File.separator + name);
    }

    public static File getFileP(String name) {
        return new File(App.context.getFilesDir().getParent() + name);
    }

    public static File getFileDB(String name) {
        return getFileP("/databases/" + name);
    }

    public static File getFileL(String link) {
        File file = getFile(link.substring(0, link.lastIndexOf("/")));
        if (!file.exists())
            file.mkdirs();
        file = getFile(link);
        return file;
    }

    public static void openInApps(String url, @Nullable String titleChooser) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            android.net.Uri uri = android.net.Uri.parse(url);
            intent.setData(uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (titleChooser == null)
                App.context.startActivity(intent);
            else
                App.context.startActivity(Intent.createChooser(intent, titleChooser));
        } catch (Exception e) {
            url = url.substring(url.indexOf(":") + 1);
            if (url.indexOf("/") == 0)
                url = url.substring(2);
            copyAddress(url);
        }
    }

    public static void copyAddress(String txt) {
        ClipboardManager clipboard = (ClipboardManager) App.context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(App.context.getString(R.string.app_name), txt);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(App.context, App.context.getString(R.string.address_copied), Toast.LENGTH_LONG).show();
    }

    public static String withOutTags(String s) {
        return android.text.Html.fromHtml(s).toString().trim();
    }
}
