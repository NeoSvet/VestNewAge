package ru.neosvet.utils;

import android.app.Activity;
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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import ru.neosvet.ui.ListItem;
import ru.neosvet.vestnewage.CalendarFragment;
import ru.neosvet.vestnewage.MainActivity;
import ru.neosvet.vestnewage.SlashActivity;

public class CalendarTask extends AsyncTask<Integer, Void, Boolean> implements Serializable {
    private transient CalendarFragment frm;
    private transient Activity act;
    List<ListItem> data = new ArrayList<ListItem>();

    public CalendarTask(CalendarFragment frm) {
        setFrm(frm);
    }

    public CalendarTask(Activity act) {
        this.act = act;
    }

    public void setFrm(CalendarFragment frm) {
        this.frm = frm;
        act = frm.getActivity();
    }

    public void setAct(Activity act) {
        this.act = act;
    }

    @Override
    protected void onProgressUpdate(Void... values) {
        super.onProgressUpdate(values);
    }

    @Override
    protected void onPostExecute(Boolean result) {
        if (frm != null) {
            frm.finishLoad(result);
        } else if (act != null) {
            if (act instanceof SlashActivity)
                ((SlashActivity) act).finishLoad();
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
            downloadAds(); //ads
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void downloadAds() {
        try {
            String t = "0";
            BufferedReader br;
            File file = new File(act.getFilesDir() + File.separator + CalendarFragment.ADS);
            if (file.exists()) {
                br = new BufferedReader(new FileReader(file));
                t = br.readLine();
                br.close();
            }
            String s = "http://neosvet.ucoz.ru/ads_vna.txt";
            if (act instanceof MainActivity) {
                br = new BufferedReader(new InputStreamReader
                        (((MainActivity) act).lib.getStream(s)));
            } else {
                br = new BufferedReader(new InputStreamReader
                        (((SlashActivity) act).lib.getStream(s)));
            }
            if (isCancelled())
                return;
            s = br.readLine();
            if (Long.parseLong(s) > Long.parseLong(t)) {
                BufferedWriter bw = new BufferedWriter(new FileWriter(file));
                bw.write(System.currentTimeMillis() + Lib.N);
                while ((s = br.readLine()) != null) {
                    bw.write(s + Lib.N);
                    bw.flush();
                }
                bw.close();
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void downloadNoread() throws Exception {
        BufferedReader br;
        if (act instanceof MainActivity) {
            br = new BufferedReader(new InputStreamReader
                    (((MainActivity) act).lib.getStream(Lib.SITE)));
        } else {
            br = new BufferedReader(new InputStreamReader
                    (((SlashActivity) act).lib.getStream(Lib.SITE)));
        }
        if (isCancelled())
            return;
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
            bw.write(data.get(i).getTitle() + Lib.N);
            bw.write(data.get(i).getLink() + Lib.N);
            bw.flush();
        }
        bw.close();
        data.clear();
    }

    public void downloadCalendar(int year, int month) throws Exception {
        try {
            JSONObject json, jsonI;
            JSONArray jsonA;
            String r, s;
            DefaultHttpClient client = new DefaultHttpClient();
            HttpGet rget = new HttpGet(Lib.SITE + "?json&year="
                    + (year + 1900) + "&month=" + (month + 1));
            final String poemOutDict = "poemOutDict";
            HttpResponse res = client.execute(rget);
            if (isCancelled())
                return;
            r = EntityUtils.toString(res.getEntity());
            json = new JSONObject(r);
            int n;
            for (int i = 0; i < json.names().length(); i++) {
                s = json.names().get(i).toString();
                jsonI = json.optJSONObject(s);
                n = data.size();
                data.add(new ListItem(s.substring(s.lastIndexOf("-") + 1)));
                if (jsonI == null) { // несколько материалов за день
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
                } else { // один материал за день
                    r = jsonI.getString(DataBase.LINK);
                    data.get(n).addLink(r);
                    jsonI = jsonI.getJSONObject("data");
                    if (jsonI != null) {
                        if (jsonI.has("title2")) {
                            if (!jsonI.getString("title2").equals(""))
                                addLink(n, r + "#2");
                        }
                    }
                }
            }
            if (isCancelled())
                return;
            File file = new File(act.getFilesDir() + CalendarFragment.CALENDAR);
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
        } catch (org.json.JSONException e) {
        }
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