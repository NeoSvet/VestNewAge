package ru.neosvet.utils;

import android.os.AsyncTask;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.Date;

import ru.neosvet.vestnewage.MainActivity;
import ru.neosvet.vestnewage.SummaryFragment;
import ru.neosvet.vestnewage.SummaryReceiver;

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
        InputStream in = new BufferedInputStream(act.lib.getStream(Lib.SITE + "rss/"));
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        BufferedWriter bw = new BufferedWriter(new FileWriter(act.getFilesDir() + SummaryFragment.RSS));
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

//    private void updateBook() throws Exception {
//        List<String> title = new ArrayList<String>();
//        List<String> link = new ArrayList<String>();
//        BufferedReader br = new BufferedReader(new FileReader(act.getFilesDir() + SummaryFragment.RSS));
//        String line, d = Lib.N;
//        while ((line = br.readLine()) != null) {
//            title.add(0, line); //title
//            link.add(0, br.readLine()); //link
//            br.readLine(); //des
//            br.readLine(); //time
//        }
//        boolean b = false;
//        File f = getFile(d);
//        int i = 0;
//        BufferedWriter bw;
//        while (i < title.size()) {
//            Lib.LOG("d="+d+","+getDate(link.get(i)));
//            if (!d.equals(getDate(link.get(i)))) {
//                d = getDate(link.get(i));
//                if(b) {
//                    Lib.LOG("close1");
//                    br.close();
//                    b=false;
//                }
//                f = getFile(d);
//                if (f.exists()) {
//                    Lib.LOG("open");
//                    b=true;
//                    br = new BufferedReader(new FileReader(f));
//                } else
//                    break;
//            }
//            br.readLine(); //title
//            line = br.readLine(); //link
//            Lib.LOG("line="+line);
//            if (line != null) {
//                Lib.LOG("link="+link.get(i));
//                if (line.contains(link.get(i))) {
//                    Lib.LOG("remove="+title.get(i));
//                    link.remove(i);
//                    title.remove(i);
//                } else {
//                    Lib.LOG("close2");
//                    br.close();
//                    b=false;
//                    bw = new BufferedWriter(new FileWriter(f, true));
//                    for (; i < title.size(); i++) {
//                        if (!d.equals(getDate(link.get(i))))
//                            break;
//                        Lib.LOG("write="+title.get(i));
//                        bw.write(title.get(i) + Lib.N);
//                        bw.write(link.get(i) + Lib.N);
//                        bw.flush();
//                    }
//                    bw.close();
//                }
//            } else
//                break;
//        }
//    }
//
//    private String getDate(String link) {
//        int i = link.indexOf("/") + 4;
//        return link.substring(i, i + 5);
//    }
//
//    private File getFile(String date) {
//        return new File(act.getFilesDir() + Lib.LIST + date);
//    }
}