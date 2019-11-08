package ru.neosvet.vestnewage.workers;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.support.annotation.NonNull;

import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.ProgressModel;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.NotificationHelper;
import ru.neosvet.vestnewage.helpers.SummaryHelper;
import ru.neosvet.vestnewage.helpers.UnreadHelper;
import ru.neosvet.vestnewage.list.ListItem;
import ru.neosvet.vestnewage.model.LoaderModel;
import ru.neosvet.vestnewage.model.SummaryModel;

public class SummaryWorker extends Worker {
    private Context context;
    private ProgressModel model;
    private final String LAST_TIME = "last_time", CHECK = "check";

    public SummaryWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
    }

    private boolean isCancelled() {
        if (model == null)
            return false;
        else
            return !model.inProgress;
    }

    @NonNull
    @Override
    public Result doWork() {
        String err, name;
        name = getInputData().getString(ProgressModel.NAME);
        model = ProgressModel.getModelByName(name);
        try {
            if (getInputData().getBoolean(CHECK, false))
                initCheck();
            else {
                loadList();
                if (name.equals(SummaryModel.class.getSimpleName()))
                    updateBook();
            }
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            err = e.getMessage();
            Lib.LOG("SummaryWorker error: " + err);
        }
        return Result.failure(new Data.Builder()
                .putString(Const.ERROR, err)
                .build());
    }

    private void publishProgress(String title, String link, String des, String time) {
        model.setProgress(new Data.Builder()
                .putString(Const.TASK, Const.PAGE)
                .putStringArray(Const.LIST, new String[]{
                        title, link, des, time
                }).build());
    }

    private void initCheck() throws Exception {
        SharedPreferences pref = context.getSharedPreferences(Const.SUMMARY, Context.MODE_PRIVATE);
        SummaryHelper summaryHelper = new SummaryHelper(context);
        List<String> result;
        String link = getInputData().getString(Const.LINK);
        if (link != null) { // postpone
            result = new ArrayList<>();
            result.add(getInputData().getString(Const.DESCTRIPTION));
            result.add(link);
        } else {
            long last_time = System.currentTimeMillis() - pref.getLong(LAST_TIME, 0);
            if (pref.getInt(Const.TIME, Const.TURN_OFF)
                    == Const.TURN_OFF || last_time < 900000L) {
                Lib.LOG("SummaryWorker: too fast " + this.getId());
                return;
            }
            SharedPreferences.Editor editor = pref.edit();
            editor.putLong(LAST_TIME, System.currentTimeMillis());
            editor.apply();
            result = checkSummary();
        }
        if (result == null) { // no updates
            Lib.LOG("SummaryWorker: no updates");
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
        Lib.LOG("SummaryWorker: updates!");
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

        File file = new File(context.getFilesDir() + Const.RSS);
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

    private void downloadPages() throws Exception {
        File file = new File(context.getFilesDir() + Const.RSS);
        if (!file.exists()) return;
        LoaderTask loader = new LoaderTask(context);
        loader.initClient();
        loader.downloadStyle(false);
        BufferedReader br = new BufferedReader(new FileReader(file));
        String title, link, des, time;
        List<ListItem> data = new ArrayList<ListItem>();
        ListItem item;
        UnreadHelper unread = new UnreadHelper(context);
        List<String> links = unread.getList();
        DateHelper d;
        while ((title = br.readLine()) != null) {
            link = br.readLine();
            des = br.readLine();
            time = br.readLine();
            if (link.contains(":"))
                continue;
            d = DateHelper.putMills(context, Long.parseLong(time));
            if (unread.addLink(link, d)) {
                item = new ListItem(title, link);
                item.setDes(des);
                item.addHead(time);
                data.add(item);
            } else if (links.contains(link)) {
                publishProgress(title, link, des, time);
            }
        }
        br.close();
        unread.close();
        for (int i = data.size() - 1; i > -1; i--) {
            if (loader.downloadPage(data.get(i).getLink(), false)) {
                publishProgress(data.get(i).getTitle(), data.get(i).getLink(),
                        data.get(i).getDes(), data.get(i).getHead(0));
            }
            if (isCancelled())
                break;
        }
        data.clear();
    }

    private void updateBook() throws Exception {
        File file = new File(context.getFilesDir() + Const.RSS);
        BufferedReader br = new BufferedReader(new FileReader(file));
        String title, link, name;
        DataBase dataBase = null;
        SQLiteDatabase db = null;
        ContentValues cv;
        Cursor cursor;
        while ((title = br.readLine()) != null) {
            link = br.readLine();
            br.readLine(); //des
            br.readLine(); //time
            name = DataBase.getDatePage(link);
            if (dataBase == null || !dataBase.getName().equals(name)) {
                if (dataBase != null)
                    dataBase.close();
                dataBase = new DataBase(context, name);
                db = dataBase.getWritableDatabase();
            }
            cursor = db.query(Const.TITLE, null,
                    Const.LINK + DataBase.Q, new String[]{link},
                    null, null, null);
            if (!cursor.moveToFirst()) {
                cv = new ContentValues();
                cv.put(Const.TITLE, title);
                cv.put(Const.LINK, link);
                db.insert(Const.TITLE, null, cv);
            }
            cursor.close();
        }
        br.close();
        if (dataBase != null)
            dataBase.close();
    }

    private void loadList() throws Exception {
        NotificationHelper notifHelper = new NotificationHelper(context);
        notifHelper.cancel(NotificationHelper.NOTIF_SUMMARY);
        String line;
        Lib lib = new Lib(context);
        InputStream in = new BufferedInputStream(lib.getStream(Const.SITE + "rss/?" + System.currentTimeMillis()));
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        BufferedWriter bw = new BufferedWriter(new FileWriter(context.getFilesDir() + Const.RSS));
        while ((line = br.readLine()) != null && !isCancelled()) {
            if (line.contains("</channel>")) break;
            if (line.contains("<item>")) {
                bw.write(withOutTag(br.readLine())); //title
                bw.write(Const.N);
                line = withOutTag(br.readLine()); //link
                if (line.contains(Const.SITE2))
                    line = line.substring(Const.SITE2.length());
                else if (line.contains(Const.SITE))
                    line = line.substring(Const.SITE.length());
                bw.write(line);
                bw.write(Const.N);
                bw.write(withOutTag(br.readLine())); //des
                bw.write(Const.N);
                bw.write(DateHelper.parse(context, withOutTag(br.readLine())).getTimeInMills() + Const.N); //time
                bw.flush();
            }
        }
        bw.close();
        br.close();
    }

    public static int getListLink(Context context) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(context.getFilesDir() + Const.RSS));
        File file = LoaderModel.getFileList(context);
        BufferedWriter bw = new BufferedWriter(new FileWriter(file));
        String s;
        int k = 0;
        while (br.readLine() != null) { //title
            s = br.readLine(); //link
            bw.write(s);
            bw.newLine();
            bw.flush();
            k++;
            br.readLine(); //des
            br.readLine(); //time
        }
        bw.close();
        br.close();
        return k;
    }
}
