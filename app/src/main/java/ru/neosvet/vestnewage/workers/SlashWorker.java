package ru.neosvet.vestnewage.workers;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.model.SlashModel;

public class SlashWorker extends Worker {
    private final Context context;

    public SlashWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        SlashModel.inProgress = true;
        try {
            synchronTime();
            loadAds();
            loadNew();
            //return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
        }
        ProgressHelper.postProgress(new Data.Builder()
                .putBoolean(Const.FINISH, true)
                .build());
        return Result.success();
        //return Result.failure();
    }

    private void loadNew() throws Exception {
        String s = "http://neosvet.ucoz.ru/vna/new.txt";
        Lib lib = new Lib(context);
        BufferedInputStream in = new BufferedInputStream(lib.getStream(s));
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        long t;
        DataBase dbPage;
        s = br.readLine();
        while (!s.equals(Const.END)) {
            t = Long.parseLong(s);
            s = br.readLine(); //link
            dbPage = new DataBase(context, s);
            SQLiteDatabase db = dbPage.getWritableDatabase();
            Cursor cursor = db.query(Const.TITLE, null,
                    Const.LINK + DataBase.Q, new String[]{s},
                    null, null, null);
            if (cursor.moveToFirst()) {
                if (t > cursor.getLong(cursor.getColumnIndex(Const.TIME))) {
                    pushNew(s, dbPage.getPageTitle(cursor.getString(
                            cursor.getColumnIndex(Const.TITLE)), s));
                }
            }
            s = br.readLine();
        }
        br.close();
        in.close();
        Thread.sleep(100);
    }

    private void pushNew(String link, String title) {
        ProgressHelper.postProgress(new Data.Builder()
                .putBoolean(Const.DIALOG, true)
                .putString(Const.LINK, link)
                .putString(Const.TITLE, title)
                .build());
    }

    private void loadAds() throws Exception {
        String t = "0";
        BufferedReader br;
        File file = new File(context.getFilesDir() + File.separator + Const.ADS);
        if (file.exists()) {
            br = new BufferedReader(new FileReader(file));
            t = br.readLine();
            br.close();
        }
        String s = "http://neosvet.ucoz.ru/ads_vna.txt";
        Lib lib = new Lib(context);
        BufferedInputStream in = new BufferedInputStream(lib.getStream(s));
        br = new BufferedReader(new InputStreamReader(in));
        s = br.readLine();
        if (Long.parseLong(s) > Long.parseLong(t)) {
            BufferedWriter bw = new BufferedWriter(new FileWriter(file));
            bw.write(System.currentTimeMillis() + Const.N);
            while ((s = br.readLine()) != null) {
                if (s.contains("<u>")) {
                    int a = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
                    int b = Integer.parseInt(s.substring(3));
                    if (b <= a) {
                        br.readLine(); //<d>
                        br.readLine(); //<e>
                        continue;
                    }
                }
                bw.write(s + Const.N);
                bw.flush();
            }
            bw.close();
        }
        br.close();
        in.close();
    }

    private void synchronTime() throws Exception {
        Request.Builder builderRequest = new Request.Builder();
        builderRequest.url(Const.SITE);
        builderRequest.header(Const.USER_AGENT, context.getPackageName());
        OkHttpClient client = Lib.createHttpClient();
        Response response = client.newCall(builderRequest.build()).execute();
        String s = response.headers().value(1);
        long timeServer = DateHelper.parse(context, s).getTimeInSeconds();
        response.close();
        long timeDevice = DateHelper.initNow(context).getTimeInSeconds();
        int timeDiff = (int) (timeDevice - timeServer);
        SharedPreferences pref = context.getSharedPreferences(Const.PROM, Context.MODE_PRIVATE);
        if (timeDiff != pref.getInt(Const.TIMEDIFF, 0)) {
            SharedPreferences.Editor editor = pref.edit();
            editor.putInt(Const.TIMEDIFF, timeDiff);
            editor.apply();
            ProgressHelper.postProgress(new Data.Builder()
                    .putBoolean(Const.TIME, true)
                    .build());
        }
    }
}
