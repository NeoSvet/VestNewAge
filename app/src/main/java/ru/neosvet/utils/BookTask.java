package ru.neosvet.utils;

import android.os.AsyncTask;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import ru.neosvet.blagayavest.BookActivity;
import ru.neosvet.ui.ListItem;
import ru.neosvet.ui.MyActivity;

/**
 * Created by NeoSvet on 26.12.2016.
 */

public class BookTask extends AsyncTask<Byte, Void, Boolean> implements Serializable {
    private transient MyActivity act;
    private List<ListItem> data = new ArrayList<ListItem>();

    public BookTask(MyActivity act) {
        this.act = act;
    }

    public void setAct(BookActivity act) {
        this.act = act;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);
        if (act instanceof BookActivity) {
            ((BookActivity) act).finishLoad(result);
        }
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected void onProgressUpdate(Void... values) {
        super.onProgressUpdate(values);
    }

    @Override
    protected Boolean doInBackground(Byte... params) {
        try {
            downloadData(params[0] == 0);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public void downloadData(boolean boolKat) throws Exception {
        String url = Lib.SITE + (boolKat ? "poems" : "tolkovaniya") + Lib.print;
        InputStream in = new BufferedInputStream(act.lib.getStream(url));
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        File file = new File(act.getFilesDir() + Lib.LIST);
        if (!file.exists())
            file.mkdir();
        boolean b = false;
        int i, n;
        String t, s, f1 = "", f2;
        while ((t = br.readLine()) != null) {
            if (!b) {
                b = t.contains("h2");//razdel
            } else if (t.contains(Lib.HREF)) {
                n = 0;
                while (t.indexOf(Lib.HREF, n) > -1) {
                    n = t.indexOf(Lib.HREF, n) + 7;
                    s = t.substring(n, t.indexOf("'", n)); //)-5
                    i = s.indexOf(".") + 1;
                    f2 = s.substring(i, i + 5);
                    if (!f2.equals(f1)) {
                        if (!boolKat) f1 += "p";
                        saveData(f1);
                        f1 = f2;
                    }
                    data.add(new ListItem(t.substring(t.indexOf(">", n)
                            + 1, t.indexOf("<", n)), s));
                }
                if (!boolKat) f1 += "p";
                saveData(f1);
            }
        }
    }

    private void saveData(String name) throws Exception {
        if (data.size() > 0) {
            File file = new File(act.getFilesDir() + Lib.LIST + name);
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file)));
            for (int i = 0; i < data.size(); i++) {
                bw.write(data.get(i).getTitle() + Lib.N);
                bw.write(data.get(i).getLink() + Lib.N);
                bw.flush();
            }
            bw.close();
            data.clear();
        }
    }
}
