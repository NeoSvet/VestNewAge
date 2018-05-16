package ru.neosvet.vestnewage.task;

import android.os.AsyncTask;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Serializable;

import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.fragment.CabmainFragment;

public class CabTask extends AsyncTask<String, Integer, String> implements Serializable {
    private final String HOST = "http://o53xo.n52gw4tpozsw42lzmexgk5i.cmle.ru/";
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
        Request.Builder builderRequest = new Request.Builder();
        builderRequest.url(HOST);
        builderRequest.header(Const.USER_AGENT, act.getPackageName());
        OkHttpClient client = Lib.createHttpClient();
        Response response = client.newCall(builderRequest.build()).execute();

        String cookie = response.header(Const.SET_COOKIE);
        cookie = cookie.substring(0, cookie.indexOf(";"));
        frm.setCookie(cookie);
        response.close();

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("user", email)
                .addFormDataPart("pass", pass)
                .build();
        builderRequest.url(HOST + "auth.php");
        builderRequest.post(requestBody);
        builderRequest.addHeader(Const.COOKIE, cookie);

        response = client.newCall(builderRequest.build()).execute();
        BufferedReader br = new BufferedReader(response.body().charStream(), 1000);
        String s = br.readLine();
        br.close();

        if (s.length() == 2) { // ok
            return getListWord(cookie, true);
        } else { // incorrect password
            return Const.AND + s;
        }
    }

    private String getListWord(String cookie, boolean returnSelectWord) throws Exception {
        Request.Builder builderRequest = new Request.Builder();
        builderRequest.url(HOST + "edinenie/anketa.html");
        builderRequest.header(Const.USER_AGENT, act.getPackageName());
        builderRequest.addHeader(Const.COOKIE, cookie);

        OkHttpClient client = Lib.createHttpClient();
        Response response = client.newCall(builderRequest.build()).execute();

        BufferedReader br = new BufferedReader(new InputStreamReader(
                response.body().byteStream(), Const.ENCODING), 1000);
        StringBuilder list = new StringBuilder();
        String s = br.readLine();
        while (!s.contains("lt_box") && !s.contains("name=\"keyw")) { //!s.contains("fd_box") &&
            s = br.readLine();
            if (s == null) {
                s = " ";
                break;
            }
        }
        br.close();
        if (s.contains("lt_box")) {  // incorrect time s.contains("fd_box") ||
            s = s.replace(Const.BR, Const.N);
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
                    if (returnSelectWord)
                        return act.getResources().getString(R.string.selected) + " " + m[i];
                }
                list.append(m[i]);
                list.append(Const.N);
            }
            if (returnSelectWord)
                return act.getResources().getString(R.string.select_status);
            list.delete(list.length() - 1, list.length());
            return list.toString();
        } else {
            return act.getResources().getString(R.string.anketa_failed);
        }
    }

    private String sendWord(String email, String cookie, int index) throws Exception {
        Request.Builder builderRequest = new Request.Builder();
        builderRequest.url(HOST + "savedata.php");
        builderRequest.header(Const.USER_AGENT, act.getPackageName());
        builderRequest.addHeader(Const.COOKIE, cookie);
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("keyw", String.valueOf(index + 1))
                .addFormDataPart("login", email)
                .addFormDataPart("hash", "")
                .build();
        builderRequest.post(requestBody);

        OkHttpClient client = Lib.createHttpClient();
        Response response = client.newCall(builderRequest.build()).execute();

        BufferedReader br = new BufferedReader(new InputStreamReader(
                response.body().byteStream(), Const.ENCODING), 1000);
        String s = br.readLine();
        br.close();

        if (s == null) { //no error
            return "ok" + index;
        } else {
            return s;
        }
    }
}