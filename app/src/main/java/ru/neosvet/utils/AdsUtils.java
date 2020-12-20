package ru.neosvet.utils;

import android.app.Activity;
import android.content.DialogInterface;
import android.view.View;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import ru.neosvet.ui.dialogs.CustomDialog;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.list.ListAdapter;
import ru.neosvet.vestnewage.list.ListItem;

public class AdsUtils {
    private CustomDialog alert;
    private Activity act;
    private long time = 0;
    private int index_ads = -1;
    private File file;

    public AdsUtils(Activity act) {
        this.act = act;
        file = new File(act.getFilesDir() + File.separator + Const.ADS);
    }

    public int getIndex() {
        return index_ads;
    }

    public void setIndex(int index) {
        index_ads = index;
    }

    public long getTime() throws Exception {
        if (time > 0)
            return time;

        if (file.exists()) {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String s = br.readLine(); //time
            br.close();
            time = Long.parseLong(s);
            return time;
        }
        return 0;
    }

    public void loadList(ListAdapter list, boolean withAd) throws Exception {
        if (file.exists()) {
            BufferedReader br = new BufferedReader(new FileReader(file));
            br.readLine(); //time
            String ad, t, s;
            if (withAd)
                ad = act.getResources().getString(R.string.ad) + ": ";
            else
                ad = "";
            int n;
            final String end = "<e>";
            while ((s = br.readLine()) != null) {
                if (s.contains("<t>")) {
                    list.insertItem(0, new ListItem(ad + s.substring(3)));
                } else if (s.contains("<u>")) {
                    n = Integer.parseInt(s.substring(3));
                    if (n > act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionCode) {
                        list.insertItem(0, new ListItem(ad + act.getResources().getString(R.string.access_new_version)));
                        list.getItem(0).addLink(act.getResources().getString(R.string.url_on_app));
                    } else {
                        while (!s.equals(end))
                            s = br.readLine();
                    }
                } else if (s.contains("<d>")) {
                    t = s.substring(3);
                    s = br.readLine();
                    while (!s.equals(end)) {
                        t += Const.N + s;
                        s = br.readLine();
                    }
                    list.getItem(0).addHead(t);
                } else if (s.contains("<l>")) {
                    list.getItem(0).addLink(s.substring(3));
                }
            }
            br.close();
        }
        list.notifyDataSetChanged();
    }

    public void clear() {
        try {
            if (file.exists()) {
                BufferedReader br = null;
                br = new BufferedReader(new FileReader(file));
                String t = br.readLine(); // читаем время последней загрузки объявлений
                br.close();
                BufferedWriter bw = new BufferedWriter(new FileWriter(file));
                bw.write(t); // затираем файл объявлений, оставляем лишь время загрузки
                bw.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showAd(final String link, String des) {
        if (des.equals("")) {// only link
            Lib lib = new Lib(act);
            lib.openInApps(link, null);
            index_ads = -1;
            return;
        }

        alert = new CustomDialog(act);
        alert.setTitle(act.getResources().getString(R.string.ad));
        alert.setMessage(des);

        if (link.equals("")) { // only des
            alert.setRightButton(act.getResources().getString(android.R.string.ok), new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    alert.dismiss();
                }
            });
        } else {
            alert.setRightButton(act.getResources().getString(R.string.open_link), new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Lib lib = new Lib(act);
                    lib.openInApps(link, null);
                    alert.dismiss();
                }
            });
        }

        alert.show(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                index_ads = -1;
            }
        });
    }
}
