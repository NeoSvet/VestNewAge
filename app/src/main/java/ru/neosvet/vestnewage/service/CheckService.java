package ru.neosvet.vestnewage.service;

import android.app.Service;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Observer;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.List;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.ProgressModel;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.helpers.NotificationHelper;
import ru.neosvet.vestnewage.helpers.SummaryHelper;
import ru.neosvet.vestnewage.model.LoaderModel;
import ru.neosvet.vestnewage.workers.CheckWorker;
import ru.neosvet.vestnewage.workers.LoaderWorker;

/**
 * Created by NeoSvet on 10.02.2018.
 */

public class CheckService extends Service implements Observer<Data> {
    public static MutableLiveData<Data> progress = new MutableLiveData<Data>();
    private List<String> list = new ArrayList<>();
    private LiveData<WorkInfo> state;
    private Observer<WorkInfo> finish = new Observer<WorkInfo>() {
        @Override
        public void onChanged(WorkInfo workInfo) {
            Lib.LOG("finish onChanged");
            if (workInfo.getState().isFinished()) {
                progress.removeObserver(CheckService.this);
                state.removeObserver(finish);
                makeNotification();
                CheckService.this.stopForeground(true);
                Lib.LOG("CheckService stop");
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Lib.LOG("CheckService onStartCommand");
        Context context = getApplicationContext();
        NotificationHelper notifHelper = new NotificationHelper(context);
        //PendingIntent piEmpty = PendingIntent.getActivity(context, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder notifBuilder = notifHelper.getNotification(
                context.getResources().getString(R.string.site_name),
                context.getResources().getString(R.string.check_new),
                NotificationHelper.CHANNEL_SUMMARY)
                //.setContentIntent(piEmpty)
                .setGroup(NotificationHelper.GROUP_SUMMARY);
        startForeground(NotificationHelper.NOTIF_SUMMARY, notifBuilder.build());

        progress.observeForever(this);
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build();
        OneTimeWorkRequest task = new OneTimeWorkRequest
                .Builder(CheckWorker.class)
                .setConstraints(constraints)
                .build();
        WorkManager work = WorkManager.getInstance();
        Data data = new Data.Builder()
                .putString(ProgressModel.NAME, this.getClass().getSimpleName())
                .build();
        WorkContinuation job = work.beginUniqueWork(LoaderModel.TAG,
                ExistingWorkPolicy.REPLACE, task);
        task = new OneTimeWorkRequest
                .Builder(LoaderWorker.class)
                .setConstraints(constraints)
                .setInputData(data)
                .build();
        job.enqueue();

        state = work.getWorkInfoByIdLiveData(task.getId());
        state.observeForever(finish);

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onChanged(@Nullable Data data) {
        Lib.LOG("progress onChanged");
        list.add(data.getString(Const.TITLE));
        list.add(data.getString(Const.LINK));
    }

    private void makeNotification() {
        if (list.size() == 0)
            return;
        SummaryHelper summaryHelper = new SummaryHelper(getApplicationContext());
        boolean several = list.size() > 2;
        boolean notNotify = several && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP;
        int start, end, step;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            start = 0;
            end = list.size();
            step = 2;
        } else {
            start = list.size() - 2;
            end = -2;
            step = -2;
        }
        for (int i = start; i != end; i += step) {
            if (summaryHelper.isNotification() && !notNotify)
                summaryHelper.showNotification();
            summaryHelper.createNotification(list.get(i), list.get(i + 1));
            if (several)
                summaryHelper.muteNotification();
        }
        if (several) {
            if (!notNotify)
                summaryHelper.showNotification();
            summaryHelper.groupNotification();
        } else
            summaryHelper.singleNotification(list.get(0));
        summaryHelper.setPreferences();
        summaryHelper.showNotification();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Lib.LOG("CheckService onBind");
        return null;
    }
}