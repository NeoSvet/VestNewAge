package ru.neosvet.vestnewage.utils;

import java.io.File;

import ru.neosvet.vestnewage.App;

public class Lib {
//    public static void LOG(String msg) {
//        Log.d("neotag", msg);
//    }

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
}
