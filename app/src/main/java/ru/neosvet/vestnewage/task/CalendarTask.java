package ru.neosvet.vestnewage.task;

import android.app.Activity;
import android.os.AsyncTask;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.neosvet.ui.ListItem;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.activity.SlashActivity;
import ru.neosvet.vestnewage.fragment.CalendarFragment;

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

            Request.Builder builderRequest = new Request.Builder();
            builderRequest.url(Const.SITE + "?json&year="
                    + (year + 1900) + "&month=" + (month + 1));
            builderRequest.header(Const.USER_AGENT, act.getPackageName());

            OkHttpClient client = Lib.createHttpClient();
            Response response = client.newCall(builderRequest.build()).execute();

            final String poemOutDict = "poemOutDict";
            if (isCancelled())
                return;
            json = new JSONObject(response.body().string());
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

            DataBase dbMonth = new DataBase(act, fCalendar.getName());
            File fNoread = new File(act.getFilesDir() + File.separator + Const.NOREAD);
            Date dItem;
            long tNoread = 0;
            if (fNoread.exists())
                tNoread = fNoread.lastModified();
            dItem = new Date("01/" + (month < 9 ? "0" : "") + (month + 1) + "/" + (year + 1900));
            StringBuilder sNoread = new StringBuilder();
            for (int i = 0; i < data.size(); i++) {
                bw.write(data.get(i).getTitle());
                bw.write(Const.N);
                dItem.setDate(Integer.parseInt(data.get(i).getTitle()));
                for (int j = 0; j < data.get(i).getCount(); j++) {
                    bw.write(data.get(i).getLink(j));
                    bw.write(Const.N);
                    if (tNoread < dItem.getTime())
                        if (!dbMonth.existsPage(data.get(i).getLink(j))) {
                            sNoread.insert(0, Const.N);
                            sNoread.insert(0, data.get(i).getLink(j).substring(Const.LINK.length()));
                        }
                }
                bw.write(Const.N);
                bw.flush();
            }
            bw.close();
            data.clear();
            if (sNoread.length() > 0) {
                bw = new BufferedWriter(new FileWriter(fNoread, true));
                bw.write(sNoread.toString());
                bw.close();
            }
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