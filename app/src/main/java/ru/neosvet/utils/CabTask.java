package ru.neosvet.utils;

import android.os.AsyncTask;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;

import ru.neosvet.vestnewage.MainActivity;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.CabmainFragment;

public class CabTask extends AsyncTask<String, Integer, String> implements Serializable {
    private final String HOST = "http://o53xo.n52gw4tpozsw42lzmexgk5i.cmle.ru/",
            USER_AGENT = "User-Agent", COOKIE = "Cookie", ENCODING = "cp1251";
    private final int PING = 10000;
    private transient CabmainFragment frm;
    private transient MainActivity act;

    public CabTask(CabmainFragment frm) {
        setFrm(frm);
    }

    public void setFrm(CabmainFragment frm) {
        this.frm = frm;
        act = (MainActivity) frm.getActivity();
    }

    @Override
    protected String doInBackground(String... params) {
        try {
            switch (params.length) {
                case 1: // get list
                    return getListWord(params[0], false);
                case 2: // login
                    return subLogin(params[0], params[1]);
                default: // (3) send word
                    return sendWord(params[0], params[1], Integer.parseInt(params[2]));
            }
        } catch (Exception e) {
            return act.getResources().getString(R.string.load_fail);
        }
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        frm.putResultTask(result);
    }

    private String subLogin(String email, String pass) throws Exception {
        StringBuilder list = new StringBuilder();
        URL url = new URL(HOST);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(PING);
        conn.setReadTimeout(PING);
        conn.setRequestProperty(USER_AGENT, act.getPackageName());
        String cookie = conn.getHeaderField("Set-" + COOKIE);
        cookie = cookie.substring(0, cookie.indexOf(";"));
        frm.setCookie(cookie);
        conn.disconnect();
        url = new URL(HOST + "auth.php");
        conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(PING);
        conn.setReadTimeout(PING);
        conn.setRequestMethod("POST");
        String s = "user=" + email + "&pass=" + pass;
        conn.setRequestProperty("Content-Length", Integer.toString(s.length()));
        conn.setRequestProperty(USER_AGENT, act.getPackageName());
        conn.setRequestProperty(COOKIE, cookie);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        OutputStream os = conn.getOutputStream();
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os, ENCODING));
        bw.write(s);
        bw.close();
        os.close();
        InputStream in = new BufferedInputStream(conn.getInputStream());
        BufferedReader br = new BufferedReader(new InputStreamReader(in, ENCODING), 1000);
        s = br.readLine();
        br.close();
        in.close();
        conn.disconnect();
        if (s.length() == 2) { // ok
            return getListWord(cookie, true);
        } else { // incorrect password
            return s;
        }
    }

    private String getListWord(String cookie, boolean boolOne) throws Exception {
        StringBuilder list = new StringBuilder();
        URL url = new URL(HOST + "edinenie/anketa.html");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(PING);
        conn.setReadTimeout(PING);
        conn.setRequestProperty(USER_AGENT, act.getPackageName());
        conn.setRequestProperty(COOKIE, cookie);
        InputStream in = new BufferedInputStream(conn.getInputStream());
        BufferedReader br = new BufferedReader(new InputStreamReader(in, ENCODING), 1000);
        String s = br.readLine();
        while (!s.contains("lt_box") && !s.contains("name=\"keyw")) { //!s.contains("fd_box") &&
            s = br.readLine();
            if (s == null) {
                s = " ";
                break;
            }
        }
        br.close();
        in.close();
        conn.disconnect();
        if (s.contains("lt_box")) {  // incorrect time s.contains("fd_box") ||
            s = s.replace("<br>", Lib.N);
            if (s.contains(" ("))
                s = s.substring(0, s.indexOf(" ("));
            else
                s = s.substring(0, s.indexOf("</p"));
            s = s.substring(s.lastIndexOf(">") + 1);
            return s;
        } else if (s.contains("name=\"keyw")) { // list word
            s = s.substring(s.indexOf("-<") + 10, s.indexOf("</select>") - 9);
            s = s.replace("<option>", "");
            String[] m = s.split("</option>");
            for (int i = 0; i < m.length; i++) {
                if (m[i].contains("selected")) {
                    m[i] = m[i].substring(m[i].indexOf(">") + 1);
                    if (boolOne)
                        return act.getResources().getString(R.string.selected) + " " + m[i];
                }
                list.append(m[i]);
                list.append(Lib.N);
            }
            if (boolOne)
                return act.getResources().getString(R.string.select_status);
            list.delete(list.length() - 1, list.length());
            return list.toString();
        } else {
            return act.getResources().getString(R.string.anketa_failed);
        }
    }

    private String sendWord(String email, String cookie, int index) throws Exception {
        URL url = new URL(HOST + "savedata.php");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(PING);
        conn.setReadTimeout(PING);
        conn.setRequestMethod("POST");
        String s = "keyw=" + (index + 1) + "&login=" + email + "&hash=";
        conn.setRequestProperty("Content-Length", Integer.toString(s.length()));
        conn.setRequestProperty(USER_AGENT, act.getPackageName());
        conn.setRequestProperty(COOKIE, cookie);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        OutputStream os = conn.getOutputStream();
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os, ENCODING));
        bw.write(s);
        bw.close();
        os.close();
        InputStream in = new BufferedInputStream(conn.getInputStream());
        BufferedReader br = new BufferedReader(new InputStreamReader(in, ENCODING), 1000);
        s = br.readLine();
        br.close();
        in.close();
        conn.disconnect();
        if (s == null) { //no error
            return "ok" + index;
        } else {
            return s;
        }
    }
}
