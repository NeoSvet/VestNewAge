package ru.neosvet.vestnewage.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.ErrorUtils;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.NeoClient;
import ru.neosvet.vestnewage.App;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.model.CabModel;

public class CabWorker extends Worker {
    public static final int SELECTED_WORD = 1, NO_SELECTED = 2, WORD_LIST = 3, TIMEOUT = 4, ERROR = 5;

    public CabWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        ProgressHelper.setBusy(true);
        ProgressHelper.postProgress(new Data.Builder()
                .putBoolean(Const.START, true)
                .build());
        String error;
        ErrorUtils.setData(getInputData());
        try {
            String task = getInputData().getString(Const.TASK);
            Data.Builder result;
            if (task.equals(Const.LOGIN))
                result = subLogin(CabModel.email,
                        getInputData().getString(Const.PASSWORD));
            else if (task.equals(Const.GET_WORDS))
                result = getListWord(false);
            else
                result = sendWord(getInputData().getInt(Const.LIST, 0));
            result.putBoolean(Const.FINISH, true);
            ProgressHelper.postProgress(result.build());
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            ErrorUtils.setError(e);
            error = e.getMessage();
        }
        ProgressHelper.postProgress(new Data.Builder()
                .putBoolean(Const.FINISH, true)
                .putString(Const.ERROR, error)
                .build());
        return Result.failure();
    }

    private Data.Builder subLogin(String email, String pass) throws Exception {
        Request request = new Request.Builder()
                .url(Const.CAB_SITE)
                .addHeader(Const.USER_AGENT, App.context.getPackageName())
                .build();
        OkHttpClient client = NeoClient.createHttpClient();
        Response response = client.newCall(request).execute();

        String cookie = response.header(Const.SET_COOKIE);
        cookie = cookie.substring(0, cookie.indexOf(";"));
        CabModel.cookie = cookie;
        response.close();

        RequestBody requestBody = new FormBody.Builder()
                .add("user", email)
                .add("pass", pass)
                .build();
        request = new Request.Builder()
                .post(requestBody)
                .url(Const.CAB_SITE + "auth.php")
                .addHeader(Const.USER_AGENT, App.context.getPackageName())
                .addHeader(Const.COOKIE, cookie)
                .build();

        response = client.newCall(request).execute();
        BufferedReader br = new BufferedReader(response.body().charStream(), 1000);
        String s = br.readLine();
        br.close();
        response.close();
        if (s.length() == 2) { // ok
            return getListWord(true);
        } else { //INCORRECT_PASSWORD
            return new Data.Builder()
                    .putInt(Const.MODE, ERROR)
                    .putString(Const.DESCTRIPTION, s);
        }
    }

    private Data.Builder getListWord(boolean returnSelectWord) throws Exception {
        Request.Builder builderRequest = new Request.Builder();
        builderRequest.url(Const.CAB_SITE + "edinenie/anketa.html");
        builderRequest.header(Const.USER_AGENT, App.context.getPackageName());
        builderRequest.addHeader(Const.COOKIE, CabModel.cookie);

        OkHttpClient client = NeoClient.createHttpClient();
        Response response = client.newCall(builderRequest.build()).execute();

        BufferedReader br = new BufferedReader(new InputStreamReader(
                response.body().byteStream(), Const.ENCODING), 1000);
        String[] list;
        String s = br.readLine();
        while (!s.contains("lt_box") && !s.contains("name=\"keyw")) { //!s.contains("fd_box") &&
            s = br.readLine();
            if (s == null) {
                s = " ";
                break;
            }
        }
        br.close();
        response.close();
        Data.Builder result = new Data.Builder();
        if (s.contains("lt_box")) {  // incorrect time s.contains("fd_box") ||
            s = s.replace(Const.BR, Const.N);
            if (s.contains(" ("))
                s = s.substring(0, s.indexOf(" ("));
            else
                s = s.substring(0, s.indexOf("</p"));
            s = s.substring(s.lastIndexOf(">") + 1);
            result.putInt(Const.MODE, TIMEOUT);
            result.putString(Const.DESCTRIPTION, s);
            return result;
        } else if (s.contains("name=\"keyw")) { // list word
            s = s.substring(s.indexOf("-<") + 10, s.indexOf("</select>") - 9);
            s = s.replace("<option>", "");
            String[] m = s.split("</option>");
            list = new String[m.length];
            for (int i = 0; i < m.length; i++) {
                if (m[i].contains("selected")) {
                    m[i] = m[i].substring(m[i].indexOf(">") + 1);
                    if (returnSelectWord) {
                        result.putInt(Const.MODE, SELECTED_WORD);
                        result.putString(Const.DESCTRIPTION, m[i]);
                        return result;
                    }
                }
                list[i] = m[i];
            }
            if (returnSelectWord) {
                result.putInt(Const.MODE, NO_SELECTED);
                return result;
            }
            result.putInt(Const.MODE, WORD_LIST);
            result.putStringArray(Const.DESCTRIPTION, list);
            return result;
        } else {
            result.putInt(Const.MODE, ERROR);
            result.putString(Const.DESCTRIPTION, App.context.getString(R.string.anketa_failed));
            return result;
        }
    }

    private Data.Builder sendWord(int index) throws Exception {
        Request.Builder builderRequest = new Request.Builder();
        builderRequest.url(Const.CAB_SITE + "savedata.php");
        builderRequest.header(Const.USER_AGENT, App.context.getPackageName());
        builderRequest.addHeader(Const.COOKIE, CabModel.cookie);
        RequestBody requestBody = new FormBody.Builder()
                .add("keyw", String.valueOf(index + 1))
                .add("login", CabModel.email)
                .add("hash", "")
                .build();
        builderRequest.post(requestBody);

        OkHttpClient client = NeoClient.createHttpClient();
        Response response = client.newCall(builderRequest.build()).execute();

        BufferedReader br = new BufferedReader(new InputStreamReader(
                response.body().byteStream(), Const.ENCODING), 1000);
        String s = br.readLine();
        br.close();
        response.close();
        Data.Builder result = new Data.Builder();
        if (s == null) { //no error
            result.putInt(Const.MODE, SELECTED_WORD);
            result.putInt(Const.SELECT, index);
        } else {
            result.putInt(Const.MODE, ERROR);
            result.putString(Const.DESCTRIPTION, s);
        }
        return result;
    }
}
