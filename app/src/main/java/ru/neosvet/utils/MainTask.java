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
import java.util.ArrayList;
import java.util.List;

import ru.neosvet.blagayavest.MainActivity;
import ru.neosvet.ui.ListItem;
import ru.neosvet.ui.MyActivity;

/**
 * Created by NeoSvet on 21.12.2016.
 */

public class MainTask extends AsyncTask<String, Void, String> implements Serializable {
    private transient MyActivity act;
    List<ListItem> data = new ArrayList<ListItem>();

    @Override
    protected void onProgressUpdate(Void... values) {
        super.onProgressUpdate(values);
    }

    public void setAct(MainActivity act) {
        this.act = act;
    }

    public MainTask(MyActivity act) {
        this.act = act;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        if (act instanceof MainActivity) {
            ((MainActivity) act).finishLoad(result);
        }
    }

    private String withOutTags(String s) {
        int i;
        s = s.replace("&ldquo;", "“").replace("&rdquo;", "”")
                .replace("&laquo;", "«").replace("&raquo;", "»")
                .replace("&ndash;", "–").replace("&gt;", ">").replace("&nbsp;", " ");
        while ((i = s.indexOf("<")) > -1) {
            s = s.substring(0, i) + s.substring(s.indexOf(">", i) + 1);
        }
        return s.trim();
    }

    @Override
    protected String doInBackground(String... params) {
        try {
            downloadList(params[0]);
            String name = params[1];
            saveList(name);
            return name.substring(name.lastIndexOf("/"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void saveList(String file) throws Exception {
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
        int j;
        for (int i = 0; i < data.size(); i++) {
//                if(item.getLink().equals("")) return null;
            bw.write(data.get(i).getTitle() + Lib.N);
            bw.write(data.get(i).getDes() + Lib.N);
            if (data.get(i).getCount() == 1)
                bw.write(data.get(i).getLink() + Lib.N);
            else if (data.get(i).getCount() == 0)
                bw.write("@" + Lib.N);
            else {
                for (j = 0; j < data.get(i).getCount(); j++) {
                    bw.write(data.get(i).getLink(j) + Lib.N);
                    bw.write(data.get(i).getHead(j) + Lib.N);
                }
            }
            bw.write(MainActivity.END + Lib.N);
            bw.flush();
        }
        bw.close();
        data.clear();
    }

    public void downloadList(String url) throws Exception {
        String line;
        url += Lib.print;
        InputStream in = new BufferedInputStream(act.lib.getStream(url));
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        boolean b = false;
        int i, n;
        String s, d = "";
        String[] m;
        while ((line = br.readLine()) != null) {
            if (!b) {
                b = line.contains("h2") || line.contains("h3");//razdel
            }
            if (b) {
                if (line.contains("<h")) {
                    if (line.contains("&")) continue;
                    setDes(d);
                    d = "";
                    if (line.contains("h3")) {
                        line = withOutTags(line);
                        if (line.length() > 5) {
                            data.add(new ListItem(line));
                            addLink("", "@");
                        }
                    } else
                        data.add(new ListItem(withOutTags(line), true));
                } else if (line.contains("href")) {
                    m = line.split("<br />");
                    for (i = 0; i < m.length; i++) {
                        n = 0;
                        line = withOutTags(m[i]);
                        if (line.length() < 5 || line.contains(">>")) continue;
                        setDes(d);
                        d = "";
                        data.add(new ListItem(line));
                        if (m[i].contains(Lib.HREF)) {
                            while ((n = m[i].indexOf(Lib.HREF, n)) > 0) {
                                n += 6;
                                s = m[i].substring(n, m[i].indexOf(">", n) - 1);
                                if (s.contains("..")) s = s.substring(2);
                                n = m[i].indexOf(">", n) + 1;
                                addLink(withOutTags(m[i].substring(n, m[i].indexOf("<", n))), s);
                            } // links
                        } else
                            addLink("", "@");
                    } // lines
                } else if (line.contains("<p")) {
                    line = withOutTags(line);
                    if (line.length() > 5) {
                        d += line + "<br>";
                    }
                } else {
                    if (line.contains("page-title"))
                        break;
                }
            }
        }
        setDes(d);
        br.close();
    }

    private void addLink(String head, String link) {
        if (link.contains(" ")) {
            //link" target="_blank
            link = link.substring(0, link.indexOf(" ") - 1);
        }
        if (link.contains("files") || link.contains(".mp3") || link.contains(".wma")
                || link.lastIndexOf("/") == link.length() - 1)
            link = Lib.SITE + link.substring(1);
//        if (link.contains(Lib.HTML) && !link.contains("http"))
//            link = link.substring(0, link.length() - 5);
        if (link.indexOf("/") == 0)
            link = link.substring(1);
        data.get(data.size() - 1).addLink(head, link);
    }

    private void setDes(String d) {
        if (data.size() > 0) {
            if (!d.equals("")) {
                d = d.substring(0, d.length() - 4);
                int i = data.size() - 1;
                if (data.get(i).getLink().equals("#"))
                    data.add(new ListItem(d));
                else
                    data.get(i).setDes(d);
            }
        }
    }
}