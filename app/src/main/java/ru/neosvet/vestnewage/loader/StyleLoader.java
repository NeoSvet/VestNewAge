package ru.neosvet.vestnewage.loader;

import android.content.Context;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.Lib;

public class StyleLoader {
    private final Lib lib;
    private final Request.Builder builderRequest;
    private final OkHttpClient client;

    public StyleLoader(Context context, Lib lib) {
        this.lib = lib;
        builderRequest = new Request.Builder();
        builderRequest.header(Const.USER_AGENT, context.getPackageName());
        builderRequest.header("Referer", Const.SITE);
        client = Lib.createHttpClient();
    }

    public void download(boolean replaceStyle) throws Exception {
        final File fLight = lib.getFile(Const.LIGHT);
        final File fDark = lib.getFile(Const.DARK);
        if (!fLight.exists() || !fDark.exists() || replaceStyle) {
            if (lib.isMainSite())
                downloadStyleFromSite(fLight, fDark);
            else
                downloadFromUcoz(fLight, fDark);
        }
    }

    private void downloadFromUcoz(File fLight, File fDark) throws Exception {
        String site = "http://neosvet.ucoz.ru/vna/";
        builderRequest.url(site + fLight.getName());
        Response response = client.newCall(builderRequest.build()).execute();
        BufferedReader br = new BufferedReader(response.body().charStream(), 1000);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fLight)));
        String s = br.readLine();
        br.close();
        response.close();
        bw.write(s);
        bw.close();

        builderRequest.url(site + fDark.getName());
        response = client.newCall(builderRequest.build()).execute();
        br = new BufferedReader(response.body().charStream(), 1000);
        bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fDark)));
        s = br.readLine();
        br.close();
        response.close();
        bw.write(s);
        bw.close();
    }

    private void downloadStyleFromSite(File fLight, File fDark) throws Exception {
        builderRequest.url(Const.SITE + "_content/BV/style-print.min.css");
        Response response = client.newCall(builderRequest.build()).execute();
        BufferedReader br = new BufferedReader(response.body().charStream(), 1000);
        BufferedWriter bwLight = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fLight)));
        BufferedWriter bwDark = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fDark)));
        String s = br.readLine();
        br.close();
        response.close();
        int u;
        String[] m = s.split("#");
        for (int i = 1; i < m.length; i++) {
            if (i == 1)
                s = m[i].substring(m[i].indexOf("body"));
            else
                s = "#" + m[i];
            if (s.contains("P B {")) { //correct bold
                u = s.indexOf("P B {");
                s = s.substring(0, u) + s.substring(s.indexOf("}", u) + 1);
            }
            if (s.contains("content"))
                s = s.replace("15px", "5px");
            else if (s.contains("print2"))
                s = s.replace("8pt/9pt", "12pt");
            bwLight.write(s);
            s = s.replace("#333", "#ccc");
            if (s.contains("#000"))
                s = s.replace("#000", "#fff");
            else
                s = s.replace("#fff", "#000");
            bwDark.write(s);
            bwLight.flush();
            bwDark.flush();
        }
        bwLight.close();
        bwDark.close();
    }
}
