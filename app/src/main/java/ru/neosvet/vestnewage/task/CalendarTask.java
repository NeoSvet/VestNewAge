package ru.neosvet.vestnewage.task;

import android.app.Activity;
import android.os.AsyncTask;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ru.neosvet.ui.ListItem;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.Noread;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.activity.SlashActivity;
import ru.neosvet.vestnewage.fragment.CalendarFragment;

public class CalendarTask extends AsyncTask<Integer, Void, Boolean> implements Serializable {
    private transient CalendarFragment frm;
    private transient Activity act;
    private transient Lib lib;
    List<ListItem> data = new ArrayList<ListItem>();

    public CalendarTask(CalendarFragment frm) {
        setFrm(frm);
    }

    public CalendarTask(Activity act) {
        this.act = act;
        lib = new Lib(act);
    }

    public void setFrm(CalendarFragment frm) {
        this.frm = frm;
        act = frm.getActivity();
        lib = new Lib(act);
    }

    public void setAct(Activity act) {
        this.act = act;
        lib = new Lib(act);
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
            downloadCalendar(params[0], params[1], params[2] == 1);
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
                br = new BufferedReader(new InputStreamReader(lib.getStream(s)));
            } else {
                br = new BufferedReader(new InputStreamReader(lib.getStream(s)));
            }
            if (isCancelled())
                return;
            s = br.readLine();
            if (Long.parseLong(s) > Long.parseLong(t)) {
                BufferedWriter bw = new BufferedWriter(new FileWriter(file));
                bw.write(System.currentTimeMillis() + Const.N);
                while ((s = br.readLine()) != null) {
                    bw.write(s + Const.N);
                    bw.flush();
                }
                bw.close();
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void downloadCalendar(int year, int month, boolean boolNoread) throws Exception {
        try {
            JSONObject json, jsonI;
            JSONArray jsonA;
            String r, s;
            InputStream in = new BufferedInputStream(lib.getStream(Const.SITE + "?json&year="
                    + (year + 1900) + "&month=" + (month + 1)));
            BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
            s = br.readLine();
            br.close();
            if (isCancelled())
                return;

            final String poemOutDict = "poemOutDict";
            json = new JSONObject(s);
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

            File fCalendar = new File(act.getFilesDir() + CalendarFragment.FOLDER);
            fCalendar.mkdir();
            fCalendar = new File(fCalendar.toString() + File.separator + month + "." + year);
            BufferedWriter bw = new BufferedWriter(new FileWriter(fCalendar));
            Noread noread = null;
            Date dItem = null;
            if (boolNoread) {
                dItem = new Date("01/" + (month < 9 ? "0" : "") + (month + 1) + "/" + (year + 1900));
                noread = new Noread(act);
            }

            for (int i = 0; i < data.size(); i++) {
                bw.write(data.get(i).getTitle());
                bw.write(Const.N);
                for (int j = 0; j < data.get(i).getCount(); j++) {
                    bw.write(data.get(i).getLink(j));
                    bw.write(Const.N);
                    if (boolNoread) {
                        dItem.setDate(Integer.parseInt(data.get(i).getTitle()));
                        noread.addLink(data.get(i).getLink(j).replace(Const.LINK, ""), dItem);
                    }
                }
                bw.write(Const.N);
                bw.flush();
            }
            bw.close();
            data.clear();
            if (boolNoread)
                noread.close();
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