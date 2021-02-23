package ru.neosvet.vestnewage.workers;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.SlashUtils;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.DevadsHelper;
import ru.neosvet.vestnewage.helpers.LoaderHelper;
import ru.neosvet.vestnewage.helpers.SlashHelper;

public class SlashWorker extends Worker {
    private final Context context;

    public SlashWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            synchronTime();
            loadAds();
            loadNew();
            //return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
        }
        SlashHelper.postCommand(context, false);
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
        Cursor cursor;
        s = br.readLine();
        while (!s.equals(Const.END)) {
            t = Long.parseLong(s);
            s = br.readLine(); //link
            dbPage = new DataBase(context, s);
            cursor = dbPage.query(Const.TITLE, new String[]{Const.TIME},
                    Const.LINK + DataBase.Q, new String[]{s},
                    null, null, null);
            if (cursor.moveToFirst()) {
                if (t > cursor.getLong(0))
                    LoaderHelper.postCommand(context, LoaderHelper.DOWNLOAD_PAGE, s);
            }
            s = br.readLine();
            dbPage.close();
        }
        br.close();
        in.close();
        Thread.sleep(100);
    }

    private void loadAds() throws Exception {
        DevadsHelper ads = new DevadsHelper(context);
       long t = ads.getTime();
        String s = "http://neosvet.ucoz.ru/ads_vna.txt";
        Lib lib = new Lib(context);
        BufferedInputStream in = new BufferedInputStream(lib.getStream(s));
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        s = br.readLine();
        if (Long.parseLong(s) > t)
            ads.update(br);
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
        SlashUtils slash = new SlashUtils(context);
        slash.reInitProm(timeDiff);
    }
}
