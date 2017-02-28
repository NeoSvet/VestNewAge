package ru.neosvet.utils;

import android.os.AsyncTask;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import ru.neosvet.blagayavest.CalendarActivity;
import ru.neosvet.ui.ListItem;
import ru.neosvet.ui.MyActivity;

/**
 * Created by NeoSvet on 14.12.2016.
 */

public class CalendarTask extends AsyncTask<Integer, Void, Boolean> implements Serializable {
    private transient MyActivity act;
    List<ListItem> data = new ArrayList<ListItem>();

    public CalendarTask(MyActivity act) {
        this.act = act;
    }

    public void setAct(CalendarActivity act) {
        this.act = act;
    }

    @Override
    protected void onProgressUpdate(Void... values) {
        super.onProgressUpdate(values);
    }

    @Override
    protected void onPostExecute(Boolean result) {
        if (act instanceof CalendarActivity) {
            ((CalendarActivity) act).finishLoad(result);
        }
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected Boolean doInBackground(Integer... params) {
        try {
            // calendar
            downloadCalendar(params[0], params[1]);
            if (params[2] == 1) { // noread
                downloadNoread();
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void downloadNoread() throws Exception {
        BufferedReader br = new BufferedReader(
                new InputStreamReader(act.lib.getStream(Lib.SITE)));
        String s;
        while ((s = br.readLine()) != null) {
            if (s.contains("clear-unread")) break;
            if (s.contains("menuitem")) {
                int i = s.indexOf(Lib.HREF) + 7;
                String link = s.substring(i, s.indexOf("\"", i));
                i = s.indexOf(DataBase.TITLE) + 7;
                s = s.substring(i, s.indexOf("\"", i)).replace("(", " (");
                data.add(new ListItem(s, link));
            }
        }

        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
                act.openFileOutput(Lib.NOREAD, act.MODE_PRIVATE)));
        for (int i = 0; i < data.size(); i++) {
            bw.write(data.get(i).getTitle() + "\n");
            bw.write(data.get(i).getLink() + "\n");
            bw.flush();
        }
        bw.close();
        data.clear();
    }

    public void downloadCalendar(int year, int month) throws Exception {
        JSONObject json, jsonI;
        JSONArray jsonA;
        String r, s;
        DefaultHttpClient client = new DefaultHttpClient();
        HttpGet rget = new HttpGet(Lib.SITE + "?json&year="
                + (year + 1900) + "&month=" + (month + 1));
        final String poemOutDict = "poemOutDict";
        HttpResponse res = client.execute(rget);
        r = EntityUtils.toString(res.getEntity());
        json = new JSONObject(r);
        int n;
        for (int i = 0; i < json.names().length(); i++) {
            s = json.names().get(i).toString();
            jsonI = json.optJSONObject(s);
            n = data.size();
            data.add(new ListItem(s.substring(s.lastIndexOf("-") + 1)));
            if (jsonI == null) {
                jsonA = json.optJSONArray(s);
                for (int j = 0; j < jsonA.length(); j++) {
                    jsonI = jsonA.getJSONObject(j);
                    r = jsonI.getString(DataBase.LINK);
                    jsonI = jsonI.getJSONObject("data");
                    if (jsonI.has(poemOutDict)) {
                        if (jsonI.getBoolean(poemOutDict))
                            data.get(n).addLink(r);
                        else addLink(n, r);
                    } else
                        addLink(n, r);
                }
            } else {
                data.get(n).addLink(jsonI.getString(DataBase.LINK));
            }
        }

        File file = new File(act.getFilesDir() + CalendarActivity.CALENDAR);
        file.mkdir();
        file = new File(file.toString() + File.separator + month + "." + year);
        BufferedWriter bw = new BufferedWriter(new FileWriter(file));
        for (int i = 0; i < data.size(); i++) {
            bw.write(data.get(i).getTitle() + Lib.N);
            for (int j = 0; j < data.get(i).getCount(); j++) {
                bw.write(data.get(i).getLink(j) + Lib.N);
            }
            bw.write(Lib.N);
            bw.flush();
        }
        bw.close();
        data.clear();
    }

    private void addLink(int n, String link) {
        if (data.get(n).getCount() > 0) {
            String s = link.substring(link.lastIndexOf("/"));
            for (int i = 0; i < data.get(n).getCount(); i++) {
                if (data.get(n).getLink(i).contains(s))
                    return;
            }
        }
        data.get(n).addLink(link);
    }
}