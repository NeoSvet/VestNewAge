package ru.neosvet.utils;

import android.os.AsyncTask;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.util.Date;

import ru.neosvet.vestnewage.SummaryReceiver;
import ru.neosvet.vestnewage.MainActivity;
import ru.neosvet.vestnewage.SummaryFragment;

public class SummaryTask extends AsyncTask<Void, Void, Boolean> implements Serializable {
    private transient SummaryFragment frm;
    private transient MainActivity act;

    public SummaryTask(SummaryFragment frm) {
        setFrm(frm);
    }

    public SummaryTask(MainActivity act) {
        this.act = act;
    }

    public void setFrm(SummaryFragment frm) {
        this.frm = frm;
        act = (MainActivity) frm.getActivity();
    }

    @Override
    protected void onProgressUpdate(Void... values) {
        super.onProgressUpdate(values);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);
        if (frm != null) {
            frm.finishLoad(result);
        }
    }

    private String withOutTag(String s) {
        int i = s.indexOf(">") + 1;
        s = s.substring(i, s.indexOf("<", i));
        return s;
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        try {
            downloadList();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public void downloadList() throws Exception {
        SummaryReceiver.cancelNotif(act);
        String line;
        InputStream in = new BufferedInputStream(act.lib.getStream(Lib.SITE + "rss/"));
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(act.getFilesDir() + SummaryFragment.RSS)));
        while ((line = br.readLine()) != null) {
            if (line.contains("</channel>")) break;
            if (line.contains("<item>")) {
                bw.write(withOutTag(br.readLine()) + Lib.N); //title
                bw.write(withOutTag(br.readLine()).substring(Lib.SITE.length()) + Lib.N); //link
                bw.write(withOutTag(br.readLine()) + Lib.N); //des
                bw.write(Long.toString(Date.parse(withOutTag(br.readLine()))) + Lib.N); //time
                bw.flush();
            }
        }
        br.close();
        bw.close();
    }
}