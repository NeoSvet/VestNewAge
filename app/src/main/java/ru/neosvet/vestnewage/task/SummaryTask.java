package ru.neosvet.vestnewage.task;

import android.os.AsyncTask;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.Date;

import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.fragment.SummaryFragment;
import ru.neosvet.vestnewage.receiver.SummaryReceiver;

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
//            updateBook();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public void downloadList() throws Exception {
        SummaryReceiver.cancelNotif(act);
        String line;
        InputStream in = new BufferedInputStream(act.lib.getStream(Const.SITE + "rss/?" + System.currentTimeMillis()));
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        BufferedWriter bw = new BufferedWriter(new FileWriter(act.getFilesDir() + SummaryFragment.RSS));
        while ((line = br.readLine()) != null) {
            if (line.contains("</channel>")) break;
            if (line.contains("<item>")) {
                bw.write(withOutTag(br.readLine())); //title
                bw.write(Const.N);
                line = withOutTag(br.readLine()); //link
                if (line.contains(Const.SITE2))
                    bw.write(Const.LINK + line.substring(Const.SITE2.length()));
                else if (line.contains(Const.SITE))
                    bw.write(Const.LINK + line.substring(Const.SITE.length()));
                else
                    bw.write(line);
                bw.write(Const.N);
                bw.write(withOutTag(br.readLine())); //des
                bw.write(Const.N);
                bw.write(Date.parse(withOutTag(br.readLine())) + Const.N); //time
                bw.flush();
            }
        }
        bw.close();
        br.close();
    }
}