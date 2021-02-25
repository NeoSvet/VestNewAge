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
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.DevadsHelper;
import ru.neosvet.vestnewage.helpers.LoaderHelper;
import ru.neosvet.vestnewage.helpers.UnreadHelper;
import ru.neosvet.vestnewage.model.SlashModel;

public class SlashWorker extends Worker {
    private final Context context;
    private final Bundle result = new Bundle();

    public SlashWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
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
        String t, s = "http://neosvet.ucoz.ru/vna/new.txt";
        Lib lib = new Lib(context);
        BufferedInputStream in = new BufferedInputStream(lib.getStream(s));
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        long time;
        DataBase dbPage;
        Cursor cursor;
        s = br.readLine();
        List<String> titles = new ArrayList<>();
        List<String> links = new ArrayList<>();
        while (!s.equals(Const.END)) {
            time = Long.parseLong(s);
            s = br.readLine(); //link
            dbPage = new DataBase(context, s);
            cursor = dbPage.query(Const.TITLE, new String[]{Const.TITLE, Const.TIME},
                    Const.LINK + DataBase.Q, new String[]{s},
                    null, null, null);
            if (cursor.moveToFirst()) {
                if (time > cursor.getLong(cursor.getColumnIndex(Const.TIME))) {
                    LoaderHelper.postCommand(context, LoaderHelper.DOWNLOAD_PAGE, s);
                    t = cursor.getString(cursor.getColumnIndex(Const.TITLE));
                    titles.add(t);
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
        DevadsHelper ads = new DevadsHelper(context);
        long t = ads.getTime();
        String s = "http://neosvet.ucoz.ru/ads_vna.txt";
        Lib lib = new Lib(context);
        BufferedInputStream in = new BufferedInputStream(lib.getStream(s));
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        s = br.readLine();
        if (Long.parseLong(s) > t) {
            if (ads.update(br)) {
                UnreadHelper unread = new UnreadHelper(context);
                unread.setBadge(ads.getUnreadCount());
                unread.close();
                result.putBoolean(Const.ADS, true);
            }
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
        result.putBoolean(Const.TIME, true);
        result.putInt(Const.TIMEDIFF, timeDiff);
    }
}
