package ru.neosvet.vestnewage.helpers;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.arch.lifecycle.LifecycleService;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.NotificationCompat;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import java.io.File;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.model.BookModel;
import ru.neosvet.vestnewage.model.CalendarModel;
import ru.neosvet.vestnewage.model.SiteModel;
import ru.neosvet.vestnewage.model.SummaryModel;
import ru.neosvet.vestnewage.workers.BookWorker;
import ru.neosvet.vestnewage.workers.CalendarWolker;
import ru.neosvet.vestnewage.workers.LoaderWorker;
import ru.neosvet.vestnewage.workers.SiteWorker;
import ru.neosvet.vestnewage.workers.SummaryWorker;

/**
 * Created by NeoSvet on 19.11.2019.
 */

public class LoaderHelper extends LifecycleService {
    public static final String TAG = "LoaderHelper";
    public static final int notif_id = 777;
    private NotificationCompat.Builder notif;
    private NotificationManager manager;
    public static boolean start;
    public static final int STOP = -2, ALL = -1, DOWNLOAD_ALL = 0, DOWNLOAD_ID = 1,
            DOWNLOAD_YEAR = 2, DOWNLOAD_PAGE = 3, DOWNLOAD_OTKR = 4;
    private Data.Builder data;
    private Constraints constraints;
    private WorkManager work;

    @Override
    public void onCreate() {
        super.onCreate();
        work = WorkManager.getInstance();
    }

    public static void postCommand(Context context, int mode, String request) {
        Intent intent = new Intent(context, LoaderHelper.class);
        if (mode == STOP) {
            intent.putExtra(Const.FINISH, true);
            intent.putExtra(Const.ERROR, request);
        } else {
            intent.putExtra(Const.MODE, mode);
            intent.putExtra(Const.TASK, request);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.startForegroundService(intent);
        else
            context.startService(intent);
    }

    @Override
    public int onStartCommand(Intent intent, final int flags, int startId) {
        if (intent == null)
            return Service.START_NOT_STICKY;
        if (intent.getBooleanExtra(Const.FINISH, false)) {
            if (!start)
                return Service.START_NOT_STICKY;
            start = false;
            if (intent.getIntExtra(Const.MODE, 0) != STOP) {
                String msg = intent.getStringExtra(Const.ERROR);
                String title;
                if (msg == null) {
                    title = getResources().getString(R.string.load_suc_finish);
                    msg = "";
                } else
                    title = getResources().getString(R.string.error_load);
                NotificationHelper notifHelper = new NotificationHelper(this);
                Intent main = new Intent(this, MainActivity.class);
                PendingIntent piMain = PendingIntent.getActivity(this, 0, main, PendingIntent.FLAG_UPDATE_CURRENT);
                PendingIntent piEmpty = PendingIntent.getActivity(this, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
                notif = notifHelper.getNotification(title, msg,
                        NotificationHelper.CHANNEL_TIPS)
                        .setContentIntent(piMain)
                        .setFullScreenIntent(piEmpty, true);
                manager.notify(notif_id + 1, notif.build());
            }
            return Service.START_NOT_STICKY;
        }
        int mode = intent.getIntExtra(Const.MODE, STOP);
        if (mode == STOP)
            return Service.START_NOT_STICKY;
        ProgressHelper.setMax(0);
        ProgressHelper.setMessage(getResources().getString(R.string.start));
        initNotif();
        start = true;
        Lib.showToast(getApplicationContext(), getResources().getString(R.string.load_background));
        startLoad(mode, intent.getStringExtra(Const.TASK));
        manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        final Handler handler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                notif.setContentText(ProgressHelper.getMessage());
                notif.setProgress(ProgressHelper.getMax(), ProgressHelper.getProg(), false);
                manager.notify(notif_id, notif.build());
                return false;
            }
        });
        new Thread(new Runnable() {
            public void run() {
                try {
                    while (start) {
                        Thread.sleep(DateHelper.SEC_IN_MILLS);
                        if (start)
                            handler.sendEmptyMessage(0);
                    }
                    stopForeground(true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        start = false;
        super.onDestroy();
    }

    private void initNotif() {
        NotificationHelper notifHelper = new NotificationHelper(this);
        Intent main = new Intent(this, MainActivity.class);
        PendingIntent piMain = PendingIntent.getActivity(this, 0, main, PendingIntent.FLAG_UPDATE_CURRENT);
        Intent iStop = new Intent(this, LoaderHelper.class);
        iStop.putExtra(Const.FINISH, true);
        iStop.putExtra(Const.MODE, STOP);
        PendingIntent piStop = PendingIntent.getService(this, 0, iStop, PendingIntent.FLAG_CANCEL_CURRENT);
        notif = notifHelper.getNotification(
                getResources().getString(R.string.load),
                getResources().getString(R.string.start),
                NotificationHelper.CHANNEL_MUTE)
                .setSmallIcon(R.drawable.star_anim)
                .setContentIntent(piMain)
                .setAutoCancel(false)
                .addAction(0, getResources().getString(R.string.stop), piStop)
                .setProgress(0, 0, true);
        startForeground(notif_id, notif.build());
    }

    public void startLoad(int mode, String request) {
        constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build();
        data = new Data.Builder()
                .putString(Const.TASK, TAG)
                .putInt(Const.MODE, mode);
        OneTimeWorkRequest task;
        WorkContinuation job;
        switch (mode) {
            case DOWNLOAD_ALL:
                job = refreshLists(ALL);
                break;
            case DOWNLOAD_ID:
                int id = Integer.parseInt(request);
                job = refreshLists(id);
                data.putInt(Const.SELECT, id);
                break;
            case DOWNLOAD_OTKR:
                data.putBoolean(Const.OTKR, true);
                task = new OneTimeWorkRequest
                        .Builder(BookWorker.class)
                        .setInputData(data.build())
                        .setConstraints(constraints)
                        .addTag(BookModel.TAG)
                        .build();
                job = work.beginUniqueWork(TAG,
                        ExistingWorkPolicy.REPLACE, task);
                break;
            case DOWNLOAD_YEAR:// refresh list for year
                data.putInt(Const.YEAR, Integer.parseInt(request));
                task = new OneTimeWorkRequest
                        .Builder(CalendarWolker.class)
                        .setInputData(data.build())
                        .setConstraints(constraints)
                        .addTag(CalendarModel.TAG)
                        .build();
                job = work.beginUniqueWork(TAG,
                        ExistingWorkPolicy.REPLACE, task);
                break;
            default:
                return;
        }
        if (mode != DOWNLOAD_OTKR) {
            task = new OneTimeWorkRequest
                    .Builder(LoaderWorker.class)
                    .setInputData(data.build())
                    .setConstraints(constraints)
                    .addTag(TAG)
                    .build();
            job = job.then(task);
        }
        job.enqueue();
    }

    private WorkContinuation refreshLists(int id) {
        // подсчёт количества списков:
        int k = 0;
        DateHelper d = null;
        if (id == ALL || id == R.id.nav_book) {
            d = DateHelper.initToday(getApplication().getBaseContext());
            k = (d.getYear() - 2016) * 12 + d.getMonth() - 1; //poems from 02.16
            k += 9; // poslaniya (01.16-09.16)
        }
        if (id == ALL) {
            k += (d.getYear() - 2016) * 12 + d.getMonth(); //calendar from 01.16
            k += 4; //main, news, media and rss
        } else if (id == R.id.nav_site) //main, news
            k = 2;
        ProgressHelper.setMessage(getApplication().getBaseContext().getResources().getString(R.string.download_list));
        ProgressHelper.setMax(k);
        OneTimeWorkRequest task;
        WorkContinuation job = null;
        if (id == ALL) { //Summary
            task = new OneTimeWorkRequest
                    .Builder(SummaryWorker.class)
                    .setInputData(data.build())
                    .setConstraints(constraints)
                    .addTag(SummaryModel.TAG)
                    .build();
            job = work.beginUniqueWork(TAG,
                    ExistingWorkPolicy.REPLACE, task);
        }
        if (id == ALL || id == R.id.nav_site) { //Site
            task = new OneTimeWorkRequest
                    .Builder(SiteWorker.class)
                    .setInputData(data.build())
                    .setConstraints(constraints)
                    .addTag(SiteModel.TAG)
                    .build();
            if (id == ALL)
                job = job.then(task);
            else
                job = work.beginUniqueWork(TAG,
                        ExistingWorkPolicy.REPLACE, task);
        }
        if (id == ALL) { //Calendar
            data = data.putInt(Const.YEAR, ALL);
            task = new OneTimeWorkRequest
                    .Builder(CalendarWolker.class)
                    .setInputData(data.build())
                    .setConstraints(constraints)
                    .addTag(CalendarModel.TAG)
                    .build();
            job = job.then(task);
        }
        if (id == ALL || id == R.id.nav_book) { //Book
            task = new OneTimeWorkRequest
                    .Builder(BookWorker.class)
                    .setInputData(data.build())
                    .setConstraints(constraints)
                    .addTag(BookModel.TAG)
                    .build();
            if (id == ALL)
                job = job.then(task);
            else
                job = work.beginUniqueWork(TAG,
                        ExistingWorkPolicy.REPLACE, task);
        }
        return job;
    }

    public static File getFileList(Context context) {
        return new File(context.getFilesDir() + File.separator + Const.LIST);
    }
}