package ru.neosvet.vestnewage.service;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.fragment.SummaryFragment;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.NotificationHelper;
import ru.neosvet.vestnewage.helpers.SummaryHelper;
import ru.neosvet.vestnewage.helpers.UnreadHelper;

/**
 * Created by NeoSvet on 10.02.2018.
 */

public class SummaryService extends JobIntentService {
    private Context context;

    public static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, SummaryService.class, NotificationHelper.NOTIF_SUMMARY, work);
    }

    @Override
    protected void onHandleWork(@NonNull final Intent intent) {
        context = getApplicationContext();
        SharedPreferences pref = context.getSharedPreferences(Const.SUMMARY, MODE_PRIVATE);
        SummaryHelper summaryHelper = new SummaryHelper(context);
        try {
            List<String> result;
            if (intent.hasExtra(Const.LINK)) { // postpone
                result = new ArrayList<>();
                result.add(intent.getStringExtra(Const.DESCTRIPTION));
                result.add(intent.getStringExtra(Const.LINK));
            } else {
                if (pref.getInt(Const.TIME, Const.TURN_OFF) == Const.TURN_OFF)
                    return;
                result = checkSummary();
            }
            if (result == null) { // no updates
                summaryHelper.serviceFinish();
                return;
            }

            boolean several = result.size() > 2;
            boolean notNotify = several && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP;
            int start, end, step;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                start = 0;
                end = result.size();
                step = 2;
            } else {
                start = result.size() - 2;
                end = -2;
                step = -2;
            }
            for (int i = start; i != end; i += step) {
                if (summaryHelper.isNotification() && !notNotify)
                    summaryHelper.showNotification();
                summaryHelper.createNotification(result.get(i), result.get(i + 1));
                if (several)
                    summaryHelper.muteNotification();
            }
            if (several) {
                if (!notNotify)
                    summaryHelper.showNotification();
                summaryHelper.groupNotification();
            } else
                summaryHelper.singleNotification(result.get(0));
            summaryHelper.setPreferences(pref);
            summaryHelper.showNotification();
        } catch (Exception e) {
            e.printStackTrace();
        }
        summaryHelper.serviceFinish();
    }

    private List<String> checkSummary() throws Exception {
        Lib lib = new Lib(context);
        InputStream in = new BufferedInputStream(lib.getStream(Const.SITE
                + "rss/?" + System.currentTimeMillis()));
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        String s, title, link, des;
        s = br.readLine();
        while (!s.contains("item"))
            s = br.readLine();
        title = withOutTag(br.readLine());
        link = parseLink(br.readLine());
        des = withOutTag(br.readLine());
        s = withOutTag(br.readLine()); //time

        File file = new File(context.getFilesDir() + SummaryFragment.RSS);
        long secFile = 0;
        if (file.exists())
            secFile = DateHelper.putMills(context, file.lastModified()).getTimeInSeconds();
        long secList = DateHelper.parse(context, s).getTimeInSeconds();
        if (secFile > secList) { //список в загрузке не нуждается
            br.close();
            return null;
        }
        List<String> list = new ArrayList<>();
        BufferedWriter bw = new BufferedWriter(new FileWriter(file));
        UnreadHelper unread = new UnreadHelper(context);
        DateHelper d;
        LoaderTask loader = new LoaderTask(context);
        loader.initClient();
        do {
            d = DateHelper.parse(context, s);
            if (unread.addLink(link, d)) {
                loader.downloadPage(link, true);
                list.add(title);
                list.add(link);
            }
            bw.write(title);
            bw.write(Const.N);
            bw.write(link);
            bw.write(Const.N);
            bw.write(des);
            bw.write(Const.N);
            bw.write(d.getTimeInMills() + Const.N); //time
            bw.flush();
            s = br.readLine(); //</item><item> or </channel>
            if (s.contains("</channel>")) break;
            title = withOutTag(br.readLine());
            link = parseLink(br.readLine());
            des = withOutTag(br.readLine());
            s = withOutTag(br.readLine()); //time
        } while (s != null);
        bw.close();
        br.close();
        if (unread.addLink(link, d)) {
            loader.downloadPage(link, true);
            list.add(title);
            list.add(link);
        }
        unread.setBadge();
        unread.close();
        return list;
    }

    private String withOutTag(String s) {
        int i = s.indexOf(">") + 1;
        s = s.substring(i, s.indexOf("<", i));
        return s;
    }

    private String parseLink(String s) {
        s = withOutTag(s);
        if (s.contains(Const.SITE2))
            s = s.substring(Const.SITE2.length());
        else if (s.contains(Const.SITE))
            s = s.substring(Const.SITE.length());
        return s;
    }
}