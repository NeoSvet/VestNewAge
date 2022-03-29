package ru.neosvet.vestnewage.workers;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.NeoClient;
import ru.neosvet.vestnewage.App;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.DevadsHelper;
import ru.neosvet.vestnewage.helpers.LoaderHelper;
import ru.neosvet.vestnewage.model.SlashModel;
import ru.neosvet.vestnewage.storage.PageStorage;

public class SlashWorker extends Worker {
    private final Bundle result = new Bundle();

    public SlashWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            SlashModel.inProgress = true;
            synchronTime();
            loadAds();
            loadNew();
            //return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
        }
        SlashModel.post(result);
        return Result.success();
        //return Result.failure();
    }

    private void loadNew() throws Exception {
        String s = "http://neosvet.ucoz.ru/vna/new.txt";
        BufferedInputStream in = NeoClient.getStream(s);
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        long time;
        PageStorage dbPage;
        Cursor cursor;
        s = br.readLine();
        List<String> titles = new ArrayList<>();
        List<String> links = new ArrayList<>();
        while (!s.equals(Const.END)) {
            time = Long.parseLong(s);
            s = br.readLine(); //link
            dbPage = new PageStorage(s);
            cursor = dbPage.getPage(s);
            if (cursor.moveToFirst()) {
                int iTime = cursor.getColumnIndex(Const.TIME);
                if (time > cursor.getLong(iTime)) {
                    LoaderHelper.postCommand(LoaderHelper.DOWNLOAD_PAGE, s);
                    int iTitle = cursor.getColumnIndex(Const.TITLE);
                    titles.add(cursor.getString(iTitle));
                    links.add(s);
                }
            }
            s = br.readLine();
            dbPage.close();
        }
        br.close();
        in.close();
        if (titles.size() > 0) {
            String[] m = new String[]{};
            result.putBoolean(Const.PAGE, true);
            result.putStringArray(Const.TITLE, titles.toArray(m));
            result.putStringArray(Const.LINK, links.toArray(m));
        }
        Thread.sleep(100);
    }

    private void loadAds() throws Exception {
        DevadsHelper ads = new DevadsHelper(App.context);
        ads.loadAds();
        ads.close();
        result.putBoolean(Const.ADS, ads.hasNew());
        result.putInt(Const.WARN, ads.getWarnIndex());
    }

    private void synchronTime() throws Exception {
        Request.Builder builderRequest = new Request.Builder();
        builderRequest.url(NeoClient.SITE);
        builderRequest.header(NeoClient.USER_AGENT, App.context.getPackageName());
        OkHttpClient client = NeoClient.createHttpClient();
        Response response = client.newCall(builderRequest.build()).execute();
        String s = response.headers().value(1);
        long timeServer = DateHelper.parse(s).getTimeInSeconds();
        response.close();
        long timeDevice = DateHelper.initNow().getTimeInSeconds();
        int timeDiff = (int) (timeDevice - timeServer);
        result.putBoolean(Const.TIME, true);
        result.putInt(Const.TIMEDIFF, timeDiff);
    }
}
